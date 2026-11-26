// Minimal AI proxy: keeps provider API keys off the device.
// Zero dependencies (Node 18+). Gemini is the default provider, Claude is optional.
import { createServer } from "node:http";

const env = process.env;
const PORT = Number(env.PORT ?? 8787);
const APP_TOKEN = env.APP_TOKEN ?? "";
const DEFAULT_PROVIDER = env.DEFAULT_PROVIDER ?? "gemini";
const GEMINI_MODEL = env.GEMINI_MODEL ?? "gemini-2.5-flash";
const CLAUDE_MODEL = env.CLAUDE_MODEL ?? "claude-haiku-4-5-20251001";
const RATE_PER_MIN = Number(env.RATE_PER_MIN ?? 20);
const MAX_BODY = 6 * 1024 * 1024; // ~4 MB image after base64
const UPSTREAM_TIMEOUT_MS = 30_000;

// Server-side prompts: the client picks a task, never the prompt, so the proxy cannot be used
// as a free general-purpose LLM and safety wording stays consistent.
const SAFETY =
  "You support general wellness, not medical diagnosis. Never name diseases as a conclusion; " +
  "describe only visible observations and suggest seeing a doctor when something looks concerning. " +
  "Reply in the user's language: ";
const TASKS = {
  food: {
    json: true,
    system: (lang) =>
      `Identify the food in the photo. Respond ONLY with JSON: {"items":[{"name":string,"grams":number,"kcal":number,"protein_g":number,"fat_g":number,"carbs_g":number}],"confidence":"low"|"medium"|"high"}. Estimates are approximate; names in ${lang}.`,
  },
  posture: {
    json: false,
    system: (lang) => SAFETY + `${lang}. Describe visible posture asymmetries briefly and suggest 2-3 gentle exercises.`,
  },
  skin: {
    json: false,
    system: (lang) => SAFETY + `${lang}. Describe only visible skin observations (color, dryness, texture) and general care tips.`,
  },
};

const hits = new Map(); // key -> timestamps
function limited(key) {
  const now = Date.now();
  const recent = (hits.get(key) ?? []).filter((t) => now - t < 60_000);
  recent.push(now);
  hits.set(key, recent);
  return recent.length > RATE_PER_MIN;
}
setInterval(() => {
  const now = Date.now();
  for (const [k, v] of hits) if (v.every((t) => now - t >= 60_000)) hits.delete(k);
}, 60_000).unref();

async function callGemini({ system, prompt, image, mime, json }) {
  const parts = [{ text: prompt }];
  if (image) parts.push({ inline_data: { mime_type: mime, data: image } });
  const res = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent`,
    {
      method: "POST",
      headers: { "content-type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
      body: JSON.stringify({
        system_instruction: { parts: [{ text: system }] },
        contents: [{ role: "user", parts }],
        generationConfig: json ? { responseMimeType: "application/json" } : {},
      }),
      signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
    },
  );
  if (!res.ok) throw new Error(`gemini ${res.status}`);
  const data = await res.json();
  return data.candidates?.[0]?.content?.parts?.map((p) => p.text ?? "").join("") ?? "";
}

async function callClaude({ system, prompt, image, mime }) {
  const content = [];
  if (image) content.push({ type: "image", source: { type: "base64", media_type: mime, data: image } });
  content.push({ type: "text", text: prompt });
  const res = await fetch("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-api-key": env.ANTHROPIC_API_KEY,
      "anthropic-version": "2023-06-01",
    },
    body: JSON.stringify({ model: CLAUDE_MODEL, max_tokens: 1024, system, messages: [{ role: "user", content }] }),
    signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
  });
  if (!res.ok) throw new Error(`claude ${res.status}`);
  const data = await res.json();
  return data.content?.filter((b) => b.type === "text").map((b) => b.text).join("") ?? "";
}

const providers = {
  gemini: { available: () => !!env.GEMINI_API_KEY, call: callGemini },
  claude: { available: () => !!env.ANTHROPIC_API_KEY, call: callClaude },
};

function send(res, status, body) {
  res.writeHead(status, { "content-type": "application/json" });
  res.end(JSON.stringify(body));
}

async function readJson(req) {
  let size = 0;
  const chunks = [];
  for await (const c of req) {
    size += c.length;
    if (size > MAX_BODY) throw Object.assign(new Error("too large"), { status: 413 });
    chunks.push(c);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw Object.assign(new Error("invalid json"), { status: 400 });
  }
}

async function handleAnalyze(req, res) {
  const token = req.headers["x-app-token"];
  if (!APP_TOKEN || token !== APP_TOKEN) return send(res, 401, { error: "unauthorized" });
  if (limited(String(token))) return send(res, 429, { error: "rate limited" });

  const body = await readJson(req);
  const task = TASKS[body.task];
  if (!task) return send(res, 400, { error: "unknown task" });
  if (body.image && !/^image\/(jpeg|png|webp)$/.test(body.mimeType ?? "")) {
    return send(res, 400, { error: "unsupported mimeType" });
  }

  const lang = body.lang === "uk" ? "Ukrainian" : "English";
  const args = {
    system: task.system(lang),
    prompt: String(body.prompt ?? "Analyze the photo.").slice(0, 500),
    image: body.image,
    mime: body.mimeType,
    json: task.json,
  };

  // Preferred provider first, the other one (if configured) as a fallback.
  const preferred = providers[body.provider] ? body.provider : DEFAULT_PROVIDER;
  const order = [preferred, ...Object.keys(providers).filter((p) => p !== preferred)].filter((p) => providers[p]?.available());
  if (order.length === 0) return send(res, 503, { error: "no provider configured" });

  for (const name of order) {
    try {
      const text = await providers[name].call(args);
      return send(res, 200, { provider: name, text });
    } catch (err) {
      console.error(`provider ${name} failed:`, err.message);
    }
  }
  send(res, 502, { error: "all providers failed" });
}

createServer(async (req, res) => {
  try {
    if (req.method === "GET" && req.url === "/health") return send(res, 200, { ok: true });
    if (req.method === "POST" && req.url === "/v1/analyze") return await handleAnalyze(req, res);
    send(res, 404, { error: "not found" });
  } catch (err) {
    send(res, err.status ?? 500, { error: err.status ? err.message : "internal error" });
  }
}).listen(PORT, () => console.log(`proxy listening on :${PORT}, default provider: ${DEFAULT_PROVIDER}`));
