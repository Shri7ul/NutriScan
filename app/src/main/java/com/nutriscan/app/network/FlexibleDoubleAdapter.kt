package com.nutriscan.app.network

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

/**
 * Open Food Facts is community maintained, so numeric fields occasionally arrive as strings
 * (for example `"energy-kcal_100g": "250"`). A plain Double adapter would throw on those and the
 * whole product would be reported as malformed, so this adapter accepts numbers and numeric
 * strings and yields null for anything unparseable instead of failing the request.
 */
object FlexibleDoubleAdapterFactory : TypeAdapterFactory {

    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != Double::class.java && type.rawType != java.lang.Double::class.java) {
            return null
        }
        val elementAdapter = gson.getAdapter(JsonElement::class.java)

        val adapter = object : TypeAdapter<Double>() {
            override fun write(out: JsonWriter, value: Double?) {
                if (value == null) out.nullValue() else out.value(value)
            }

            override fun read(reader: JsonReader): Double? {
                val element = elementAdapter.read(reader)
                if (element == null || element.isJsonNull || !element.isJsonPrimitive) return null
                val primitive = element.asJsonPrimitive
                val parsed = when {
                    primitive.isNumber -> primitive.asDouble
                    primitive.isString -> primitive.asString
                        .trim()
                        .replace(',', '.')
                        .toDoubleOrNull()
                    else -> null
                }
                return parsed?.takeIf { it.isFinite() }
            }
        }

        @Suppress("UNCHECKED_CAST")
        return adapter as TypeAdapter<T>
    }
}
