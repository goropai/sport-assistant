package com.sportassistant.figure

import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.BasicStroke
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

class FigureRenderTest {
    private val frames = listOf(0f, 0.5f, 1f)

    @Test
    fun `every pose stays inside the scene`() {
        // Collect every violation so one run shows all the poses that need fixing.
        val problems = mutableListOf<String>()
        for ((id, def) in Figures.all) {
            for (t in frames) {
                for (p in def.frame(t).points) {
                    if (p.x !in 0.0f..WORLD_W) problems += "$id t=$t x=${p.x} is outside the scene"
                    if (p.y < WORLD_TOP + 0.06f) problems += "$id t=$t y=${p.y} is above the scene"
                    if (p.y > FLOOR_Y + 0.03f) problems += "$id t=$t y=${p.y} is below the floor"
                }
            }
        }
        assertTrue(problems.distinct().joinToString("; "), problems.isEmpty())
    }

    @Test
    fun `render contact sheet`() {
        val cellW = 320
        val cellH = 200
        val ids = Figures.all.keys.toList()
        val cols = frames.size
        val sheet = BufferedImage(cellW * cols, cellH * ids.size, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.fillRect(0, 0, sheet.width, sheet.height)

        ids.forEachIndexed { row, id ->
            val def = Figures.all.getValue(id)
            frames.forEachIndexed { col, t ->
                val ox = col * cellW
                val oy = row * cellH
                val scale = minOf(cellW / WORLD_W, cellH / WORLD_H)
                fun px(p: Pt) = (ox + p.x * scale).toInt()
                fun py(p: Pt) = (oy + (p.y - WORLD_TOP) * scale).toInt()

                g.color = Color(220, 220, 220)
                g.drawRect(ox, oy, cellW - 1, cellH - 1)
                g.stroke = BasicStroke(2f)
                g.color = Color.GRAY
                g.drawLine(ox, (oy + (FLOOR_Y - WORLD_TOP) * scale).toInt(), ox + cellW, (oy + (FLOOR_Y - WORLD_TOP) * scale).toInt())
                for ((a, b) in def.props) g.drawLine(px(a), py(a), px(b), py(b))

                val sk = def.frame(t)
                g.stroke = BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                for (s in sk.segments.sortedBy { it.near }) {
                    g.color = if (s.near) Color(30, 90, 160) else Color(150, 180, 210)
                    g.drawLine(px(s.a), py(s.a), px(s.b), py(s.b))
                }
                g.color = Color(30, 90, 160)
                val r = (sk.headRadius * scale).toInt()
                g.drawOval(px(sk.head) - r, py(sk.head) - r, r * 2, r * 2)
                g.color = Color.BLACK
                g.drawString("$id  t=$t", ox + 6, oy + 14)
            }
        }
        g.dispose()
        val out = File("build/figures/sheet.png").apply { parentFile.mkdirs() }
        ImageIO.write(sheet, "png", out)
        assertTrue(out.exists())
    }
}
