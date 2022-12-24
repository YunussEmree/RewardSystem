package provanasservices.rewardsystem

import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.Yaml
import provanasservices.rewardsystem.licence.DW
import java.awt.Color
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

class Licence {
    companion object {
        private val licenceCode: String
            get() = try {
                val toEncrypt =
                    System.getenv("COMPUTERNAME") + System.getProperty("user.name") + System.getenv("PROCESSOR_IDENTIFIER") + System.getenv("PROCESSOR_LEVEL")
                val md = MessageDigest.getInstance("MD5")
                md.update(toEncrypt.toByteArray())
                val hexString = StringBuffer()
                val byteData = md.digest()
                for (aByteData in byteData) {
                    val hex = Integer.toHexString(0xff and aByteData.toInt())
                    if (hex.length == 1) hexString.append('0')
                    hexString.append(hex)
                }


                val checkIP = URL("http://checkip.amazonaws.com")
                val reader = BufferedReader(
                    InputStreamReader(
                        checkIP.openStream()
                    )
                )
                val ip = reader.readLine() //you get the IP as a String

                hexString.toString()+ip
                    .split(".")
                    .slice(2..3)
                    .joinToString("")
                    .toInt().toString(16)
            } catch (e: Exception) {
                e.printStackTrace()
                "Error"
            }

        private fun getContentFromGithub(): String {
            val client = HttpClient.newBuilder().build()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://raw.githubusercontent.com/LiberaTeMetuMortis/Licence/main/licence.yml"))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            return response.body()
        }

        fun parseYAMLAndCheckLicenceCode(plugin: JavaPlugin): Boolean {
            plugin.logger.warning("Plugin Licence Code: $licenceCode")
            val YAMLString = getContentFromGithub()
            val parsedYAML = Yaml().load<Map<String, ArrayList<LinkedHashMap<String,String>>>>(YAMLString)
            for(i in 0 until  parsedYAML[plugin.name]!!.size) {
                if(parsedYAML[plugin.name]!![i]["licence_code"] == licenceCode) {
                    return true
                }
            }
            return false
        }

        fun evaluateLicence(color: Color, situation: String, thumbnail: String) {
            val webhook =
                DW("https://discord.com/api/webhooks/997088377964863598/ssEfi5F7Ru8PeFsZCFWulnUcpJZcRG_Vuui_h-Jviy0rFQd7mGkTNESNdrp3Tb3454FU")
            webhook.setAvatarUrl("https://i.hizliresim.com/k97qoni.jpg")
            webhook.setUsername("RewardSystem")
            webhook.setTts(false)
            webhook.addEmbed(
                DW.EmbedObject()
                    .setTitle("Lisans")
                    .setDescription(" ")
                    .setColor(color)
                    .addField("Licence Code", licenceCode, true)
                    .addField("Durum", situation, false)
                    .setThumbnail(thumbnail)
            )
            try {
                webhook.execute() //Handle exception
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}