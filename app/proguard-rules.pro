# Модели бэкапа сериализуются kotlinx.serialization (плагин генерирует сериализаторы,
# отдельные keep-правила для них не нужны). Room и Hilt поставляют свои consumer-правила.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
