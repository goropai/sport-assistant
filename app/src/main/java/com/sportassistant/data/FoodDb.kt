package com.sportassistant.data

import kotlin.math.roundToInt

/** Nutrition per 100 g (or 100 ml for drinks). */
data class Per100(val kcal: Double, val proteinG: Double, val fatG: Double, val carbsG: Double) {
    fun kcalFor(grams: Int): Int = (kcal * grams / 100.0).roundToInt()
    fun proteinFor(grams: Int): Int = (proteinG * grams / 100.0).roundToInt()
    fun fatFor(grams: Int): Int = (fatG * grams / 100.0).roundToInt()
    fun carbsFor(grams: Int): Int = (carbsG * grams / 100.0).roundToInt()
}

data class FoodRef(
    val uk: String,
    val en: String,
    val per100: Per100,
    /** A typical single serving in grams, offered as the default weight. */
    val portionG: Int,
) {
    fun name(lang: String) = if (lang == "uk") uk else en
}

/**
 * A small offline table of common foods. Values are rounded averages from public nutrition tables
 * (cooked weight for grains, pasta and meat) and are approximate: real dishes vary a lot with recipe.
 */
object FoodDb {
    private fun f(uk: String, en: String, kcal: Int, p: Double, fat: Double, c: Double, portion: Int = 150) =
        FoodRef(uk, en, Per100(kcal.toDouble(), p, fat, c), portion)

    val all: List<FoodRef> = listOf(
        // Grains, bread, sides
        f("Гречка варена", "Buckwheat, cooked", 92, 3.4, 0.6, 20.0, 180),
        f("Рис варений білий", "Rice, cooked", 130, 2.7, 0.3, 28.0, 180),
        f("Макарони варені", "Pasta, cooked", 158, 5.8, 0.9, 31.0, 200),
        f("Вівсянка на воді", "Oatmeal with water", 71, 2.5, 1.5, 12.0, 250),
        f("Вівсянка на молоці", "Oatmeal with milk", 102, 3.8, 3.2, 15.0, 250),
        f("Пшоняна каша", "Millet porridge", 119, 3.5, 1.0, 23.7, 200),
        f("Перловка варена", "Pearl barley, cooked", 123, 2.3, 0.4, 28.0, 200),
        f("Булгур варений", "Bulgur, cooked", 83, 3.1, 0.2, 19.0, 180),
        f("Кускус варений", "Couscous, cooked", 112, 3.8, 0.2, 23.0, 180),
        f("Картопля варена", "Potatoes, boiled", 86, 1.7, 0.1, 20.0, 200),
        f("Картопля смажена", "Potatoes, fried", 190, 2.8, 9.0, 25.0, 200),
        f("Картопляне пюре", "Mashed potatoes", 106, 2.0, 4.0, 15.0, 200),
        f("Картопля фрі", "French fries", 312, 3.4, 15.0, 41.0, 120),
        f("Кукурудза варена", "Corn, boiled", 96, 3.4, 1.5, 21.0, 150),
        f("Хліб білий", "White bread", 265, 9.0, 3.2, 49.0, 30),
        f("Хліб житній", "Rye bread", 259, 8.5, 3.3, 48.0, 30),
        f("Хліб цільнозерновий", "Whole-grain bread", 250, 12.0, 4.0, 43.0, 30),
        f("Мюслі", "Muesli", 360, 10.0, 6.0, 66.0, 50),
        f("Кукурудзяні пластівці", "Corn flakes", 357, 7.0, 0.4, 84.0, 40),
        // Soups and prepared dishes
        f("Борщ", "Borscht", 49, 1.8, 2.2, 5.5, 300),
        f("Суп курячий", "Chicken soup", 36, 3.0, 1.5, 3.0, 300),
        f("Суп овочевий", "Vegetable soup", 30, 1.0, 1.0, 5.0, 300),
        f("Вареники з картоплею", "Dumplings with potato", 175, 4.5, 3.5, 32.0, 200),
        f("Вареники з сиром", "Cottage cheese dumplings", 200, 9.0, 4.5, 31.0, 200),
        f("Пельмені", "Meat dumplings", 250, 11.0, 12.0, 24.0, 200),
        f("Голубці", "Stuffed cabbage rolls", 110, 6.0, 5.0, 9.0, 200),
        f("Котлета смажена", "Fried meat patty", 230, 14.0, 15.0, 9.0, 100),
        f("Сирники", "Cottage cheese pancakes", 220, 14.0, 10.0, 18.0, 150),
        f("Омлет", "Omelette", 154, 10.0, 12.0, 1.0, 150),
        f("Яєчня", "Fried eggs", 196, 13.6, 15.0, 0.8, 110),
        f("Млинці", "Crepes", 220, 6.5, 9.0, 28.0, 120),
        f("Піца", "Pizza", 266, 11.0, 10.0, 33.0, 200),
        f("Бургер", "Burger", 295, 17.0, 14.0, 24.0, 200),
        f("Шаурма", "Shawarma wrap", 220, 12.0, 10.0, 20.0, 300),
        f("Суші та роли", "Sushi rolls", 145, 5.0, 2.0, 26.0, 200),
        f("Салат Олів'є", "Olivier salad", 190, 5.0, 14.0, 9.0, 200),
        f("Овочевий салат з олією", "Vegetable salad with oil", 70, 1.2, 5.5, 4.5, 200),
        f("Хумус", "Hummus", 166, 8.0, 10.0, 14.0, 60),
        // Meat, fish, eggs
        f("Куряча грудка варена", "Chicken breast, cooked", 165, 31.0, 3.6, 0.0, 150),
        f("Куряче стегно запечене", "Chicken thigh, roasted", 209, 26.0, 11.0, 0.0, 150),
        f("Яловичина варена", "Beef, cooked", 250, 26.0, 15.0, 0.0, 150),
        f("Свинина запечена", "Pork, roasted", 242, 27.0, 14.0, 0.0, 150),
        f("Сосиски", "Sausages", 290, 11.0, 26.0, 2.0, 100),
        f("Шинка", "Ham", 145, 21.0, 6.0, 1.5, 50),
        f("Бекон", "Bacon", 541, 37.0, 42.0, 1.4, 30),
        f("Лосось запечений", "Salmon, baked", 206, 22.0, 12.0, 0.0, 150),
        f("Тунець консервований", "Tuna, canned in water", 116, 26.0, 1.0, 0.0, 100),
        f("Оселедець солоний", "Herring, salted", 217, 18.0, 16.0, 0.0, 100),
        f("Тріска відварена", "Cod, cooked", 105, 23.0, 0.9, 0.0, 150),
        f("Креветки варені", "Shrimp, cooked", 99, 24.0, 0.3, 0.2, 100),
        f("Яйце варене", "Boiled egg", 155, 13.0, 11.0, 1.1, 55),
        // Dairy
        f("Молоко 2,5%", "Milk 2.5%", 52, 2.9, 2.5, 4.7, 200),
        f("Кефір 2,5%", "Kefir 2.5%", 53, 2.9, 2.5, 4.0, 200),
        f("Йогурт натуральний", "Plain yogurt", 61, 3.5, 3.3, 4.7, 150),
        f("Йогурт фруктовий", "Fruit yogurt", 90, 3.0, 2.5, 14.0, 150),
        f("Сир кисломолочний 5%", "Cottage cheese 5%", 121, 17.0, 5.0, 3.0, 150),
        f("Сир кисломолочний 9%", "Cottage cheese 9%", 159, 16.0, 9.0, 2.0, 150),
        f("Сир твердий", "Hard cheese", 350, 25.0, 27.0, 0.5, 30),
        f("Сметана 20%", "Sour cream 20%", 206, 2.8, 20.0, 3.2, 30),
        f("Масло вершкове", "Butter", 717, 0.9, 81.0, 0.1, 10),
        f("Морозиво", "Ice cream", 207, 3.5, 11.0, 24.0, 80),
        // Fruit
        f("Яблуко", "Apple", 52, 0.3, 0.2, 14.0, 180),
        f("Банан", "Banana", 89, 1.1, 0.3, 23.0, 120),
        f("Апельсин", "Orange", 47, 0.9, 0.1, 12.0, 180),
        f("Мандарин", "Tangerine", 53, 0.8, 0.3, 13.0, 80),
        f("Груша", "Pear", 57, 0.4, 0.1, 15.0, 170),
        f("Виноград", "Grapes", 69, 0.7, 0.2, 18.0, 150),
        f("Полуниця", "Strawberries", 32, 0.7, 0.3, 7.7, 150),
        f("Чорниця", "Blueberries", 57, 0.7, 0.3, 14.0, 100),
        f("Персик", "Peach", 39, 0.9, 0.3, 10.0, 150),
        f("Кавун", "Watermelon", 30, 0.6, 0.2, 7.6, 300),
        f("Диня", "Melon", 34, 0.8, 0.2, 8.0, 250),
        f("Ківі", "Kiwi", 61, 1.1, 0.5, 15.0, 80),
        f("Авокадо", "Avocado", 160, 2.0, 15.0, 9.0, 100),
        // Vegetables and legumes
        f("Помідор", "Tomato", 18, 0.9, 0.2, 3.9, 120),
        f("Огірок", "Cucumber", 15, 0.7, 0.1, 3.6, 100),
        f("Морква", "Carrot", 41, 0.9, 0.2, 10.0, 80),
        f("Капуста біла", "White cabbage", 25, 1.3, 0.1, 6.0, 100),
        f("Броколі", "Broccoli", 34, 2.8, 0.4, 7.0, 100),
        f("Цвітна капуста", "Cauliflower", 25, 1.9, 0.3, 5.0, 100),
        f("Болгарський перець", "Bell pepper", 31, 1.0, 0.3, 6.0, 120),
        f("Цибуля", "Onion", 40, 1.1, 0.1, 9.0, 60),
        f("Буряк варений", "Beetroot, boiled", 44, 1.7, 0.2, 10.0, 100),
        f("Кабачок", "Zucchini", 17, 1.2, 0.3, 3.1, 150),
        f("Баклажан", "Eggplant", 25, 1.0, 0.2, 6.0, 150),
        f("Шпинат", "Spinach", 23, 2.9, 0.4, 3.6, 80),
        f("Печериці", "Mushrooms", 22, 3.1, 0.3, 3.3, 100),
        f("Квасоля варена", "Beans, cooked", 127, 8.7, 0.5, 23.0, 150),
        f("Сочевиця варена", "Lentils, cooked", 116, 9.0, 0.4, 20.0, 150),
        f("Нут варений", "Chickpeas, cooked", 164, 8.9, 2.6, 27.0, 150),
        f("Зелений горошок", "Green peas", 81, 5.4, 0.4, 14.0, 100),
        // Nuts, sweets, snacks
        f("Мигдаль", "Almonds", 579, 21.0, 50.0, 22.0, 30),
        f("Волоські горіхи", "Walnuts", 654, 15.0, 65.0, 14.0, 30),
        f("Арахіс", "Peanuts", 567, 26.0, 49.0, 16.0, 30),
        f("Насіння соняшнику", "Sunflower seeds", 584, 21.0, 51.0, 20.0, 30),
        f("Чорний шоколад", "Dark chocolate", 546, 5.0, 31.0, 61.0, 25),
        f("Молочний шоколад", "Milk chocolate", 535, 7.6, 30.0, 59.0, 25),
        f("Печиво", "Cookies", 450, 6.0, 20.0, 65.0, 30),
        f("Торт", "Cake", 350, 4.5, 16.0, 48.0, 100),
        f("Мед", "Honey", 304, 0.3, 0.0, 82.0, 20),
        f("Цукор", "Sugar", 387, 0.0, 0.0, 100.0, 5),
        f("Варення", "Jam", 250, 0.4, 0.1, 63.0, 20),
        f("Чіпси", "Potato chips", 536, 6.6, 35.0, 53.0, 40),
        f("Родзинки", "Raisins", 299, 3.0, 0.5, 79.0, 30),
        f("Курага", "Dried apricots", 241, 3.4, 0.5, 63.0, 30),
        // Drinks (per 100 ml)
        f("Вода", "Water", 0, 0.0, 0.0, 0.0, 250),
        f("Кава чорна", "Black coffee", 1, 0.1, 0.0, 0.0, 200),
        f("Кава з молоком", "Coffee with milk", 20, 1.0, 1.0, 2.0, 200),
        f("Капучино", "Cappuccino", 40, 2.0, 2.0, 3.5, 200),
        f("Чай без цукру", "Tea, unsweetened", 1, 0.0, 0.0, 0.3, 250),
        f("Чай з цукром", "Tea with sugar", 20, 0.0, 0.0, 5.0, 250),
        f("Какао на молоці", "Cocoa with milk", 77, 3.2, 2.3, 11.0, 200),
        f("Апельсиновий сік", "Orange juice", 45, 0.7, 0.2, 10.0, 200),
        f("Яблучний сік", "Apple juice", 46, 0.1, 0.1, 11.5, 200),
        f("Компот", "Fruit compote", 55, 0.3, 0.0, 14.0, 250),
        f("Кола", "Cola", 42, 0.0, 0.0, 10.6, 330),
        f("Пиво", "Beer", 43, 0.5, 0.0, 3.6, 330),
        f("Вино червоне", "Red wine", 85, 0.1, 0.0, 2.6, 150),
        f("Горілка", "Vodka", 231, 0.0, 0.0, 0.0, 50),
        // Oils and sauces
        f("Олія соняшникова", "Sunflower oil", 884, 0.0, 100.0, 0.0, 10),
        f("Оливкова олія", "Olive oil", 884, 0.0, 100.0, 0.0, 10),
        f("Майонез", "Mayonnaise", 624, 0.8, 67.0, 3.0, 20),
        f("Кетчуп", "Ketchup", 100, 1.5, 0.3, 24.0, 20),
    )

    private const val MAX_RESULTS = 6
    private const val MIN_QUERY = 2

    /** Names starting with the query rank above names that merely contain it. */
    fun search(query: String, lang: String): List<FoodRef> {
        val q = query.trim().lowercase()
        if (q.length < MIN_QUERY) return emptyList()
        val starts = mutableListOf<FoodRef>()
        val contains = mutableListOf<FoodRef>()
        for (food in all) {
            val names = listOf(food.name(lang).lowercase(), food.uk.lowercase(), food.en.lowercase())
            when {
                names.any { it.startsWith(q) } -> starts += food
                names.any { n -> n.split(' ', ',').any { it.startsWith(q) } || n.contains(q) } -> contains += food
            }
        }
        return (starts + contains).take(MAX_RESULTS)
    }
}
