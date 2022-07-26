package provanasservices.rewardsystem.licence

import java.awt.Color
import java.io.IOException
import java.lang.reflect.Array
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class DW(private val url: String) {
    private var content: String? = null
    private var username: String? = null
    private var avatarUrl: String? = null
    private var tts = false
    private val embeds: MutableList<EmbedObject> = ArrayList()
    fun setContent(content: String?) {
        this.content = content
    }

    fun setUsername(username: String?) {
        this.username = username
    }

    fun setAvatarUrl(avatarUrl: String?) {
        this.avatarUrl = avatarUrl
    }

    fun setTts(tts: Boolean) {
        this.tts = tts
    }

    fun addEmbed(embed: EmbedObject) {
        embeds.add(embed)
    }

    @Throws(IOException::class)
    fun execute() {
        if (content == null && embeds.isEmpty()) {
            throw IllegalArgumentException("Set content or add at least one EmbedObject")
        }
        val json = JSONObject()
        json.put("content", content)
        json.put("username", username)
        json.put("avatar_url", avatarUrl)
        json.put("tts", tts)
        if (embeds.isNotEmpty()) {
            val embedObjects: MutableList<JSONObject> = ArrayList()
            for (embed: EmbedObject in embeds) {
                val jsonEmbed = JSONObject()
                jsonEmbed.put("title", embed.title)
                jsonEmbed.put("description", embed.description)
                jsonEmbed.put("url", embed.url)
                if (embed.color != null) {
                    val color = embed.color
                    var rgb = color!!.red
                    rgb = (rgb shl 8) + color.green
                    rgb = (rgb shl 8) + color.blue
                    jsonEmbed.put("color", rgb)
                }
                val footer = embed.footer
                val image = embed.image
                val thumbnail = embed.thumbnail
                val author = embed.author
                val fields = embed.getFields()
                if (footer != null) {
                    val jsonFooter = JSONObject()
                    jsonFooter.put("text", footer.text)
                    jsonFooter.put("icon_url", footer.iconUrl)
                    jsonEmbed.put("footer", jsonFooter)
                }
                if (image != null) {
                    val jsonImage = JSONObject()
                    jsonImage.put("url", image.url)
                    jsonEmbed.put("image", jsonImage)
                }
                if (thumbnail != null) {
                    val jsonThumbnail = JSONObject()
                    jsonThumbnail.put("url", thumbnail.url)
                    jsonEmbed.put("thumbnail", jsonThumbnail)
                }
                if (author != null) {
                    val jsonAuthor = JSONObject()
                    jsonAuthor.put("name", author.name)
                    jsonAuthor.put("url", author.url)
                    jsonAuthor.put("icon_url", author.iconUrl)
                    jsonEmbed.put("author", jsonAuthor)
                }
                val jsonFields: MutableList<JSONObject> = ArrayList()
                for (field: EmbedObject.Field in fields) {
                    val jsonField = JSONObject()
                    jsonField.put("name", field.name)
                    jsonField.put("value", field.value)
                    jsonField.put("inline", field.inline)
                    jsonFields.add(jsonField)
                }
                jsonEmbed.put("fields", jsonFields.toTypedArray())
                embedObjects.add(jsonEmbed)
            }
            json.put("embeds", embedObjects.toTypedArray())
        }
        val url = URL(url)
        val connection = url.openConnection() as HttpsURLConnection
        connection.addRequestProperty("Content-Type", "application/json")
        connection.addRequestProperty("User-Agent", "Java-DiscordWebhook-BY-Gelox_")
        connection.doOutput = true
        connection.requestMethod = "POST"
        val stream = connection.outputStream
        stream.write(json.toString().toByteArray())
        stream.flush()
        stream.close()
        connection.inputStream.close()
        connection.disconnect()
    }

    class EmbedObject {
        var title: String? = null
            private set
        var description: String? = null
            private set
        var url: String? = null
            private set
        var color: Color? = null
            private set
        var footer: Footer? = null
            private set
        var thumbnail: Thumbnail? = null
            private set
        var image: Image? = null
            private set
        var author: Author? = null
            private set
        private val fields: MutableList<Field> = ArrayList()
        fun getFields(): List<Field> {
            return fields
        }

        fun setTitle(title: String?): EmbedObject {
            this.title = title
            return this
        }

        fun setDescription(description: String?): EmbedObject {
            this.description = description
            return this
        }

        fun setUrl(url: String?): EmbedObject {
            this.url = url
            return this
        }

        fun setColor(color: Color?): EmbedObject {
            this.color = color
            return this
        }

        fun setFooter(text: String?, icon: String?): EmbedObject {
            footer = Footer(text, icon)
            return this
        }

        fun setThumbnail(url: String?): EmbedObject {
            thumbnail = Thumbnail(url)
            return this
        }

        fun setImage(url: String?): EmbedObject {
            image = Image(url)
            return this
        }

        fun setAuthor(name: String?, url: String?, icon: String?): EmbedObject {
            author = Author(name, url, icon)
            return this
        }

        fun addField(name: String?, value: String?, inline: Boolean): EmbedObject {
            fields.add(Field(name, value, inline))
            return this
        }


        data class Footer(val text: String?, val iconUrl: String?)
        data class Thumbnail(val url: String?)
        data class Image(val url: String?)
        data class Author(val name: String?, val url: String?, val iconUrl: String?)
        data class Field(val name: String?, val value: String?, val inline: Boolean)


    }

    private inner class JSONObject {
        private val map = HashMap<String, Any>()
        fun put(key: String, value: Any?) {
            if (value != null) {
                map[key] = value
            }
        }

        override fun toString(): String {
            val builder = StringBuilder()
            val entrySet: Set<Map.Entry<String, Any>> = map.entries
            builder.append("{")
            for ((i, entry: Map.Entry<String, Any>) in entrySet.withIndex()) {
                val value = entry.value
                builder.append(quote(entry.key)).append(":")
                if (value is String) {
                    builder.append(quote(value.toString()))
                } else if (value is Int) {
                    builder.append(Integer.valueOf(value.toString()))
                } else if (value is Boolean) {
                    builder.append(value)
                } else if (value is JSONObject) {
                    builder.append(value.toString())
                } else if (value.javaClass.isArray) {
                    builder.append("[")
                    val len = Array.getLength(value)
                    for (j in 0 until len) {
                        builder.append(Array.get(value, j).toString()).append(if (j != len - 1) "," else "")
                    }
                    builder.append("]")
                }
                builder.append(if (i + 1 == entrySet.size) "}" else ",")
            }
            return builder.toString()
        }

        private fun quote(string: String): String {
            return "\"" + string + "\""
        }
    }
}