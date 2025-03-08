package provanasservices.rewardsystem.util

import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.Yaml
import provanasservices.rewardsystem.service.LoggingService
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

/**
 * Handles license verification for the plugin.
 * Generates a machine-specific license code and validates it against a remote registry.
 */
class Licence {
    companion object {
        /**
         * Generates a machine-specific license code based on hardware and user information.
         * 
         * @return MD5 hash representing the machine-specific license code
         */
        private val licenceCode: String
            get() = try {
                // Combine machine-specific identifiers
                val toEncrypt = System.getenv("COMPUTERNAME") + 
                               System.getProperty("user.name") + 
                               System.getenv("PROCESSOR_IDENTIFIER") + 
                               System.getenv("PROCESSOR_LEVEL")
                
                // Generate MD5 hash
                val md = MessageDigest.getInstance("MD5")
                md.update(toEncrypt.toByteArray())
                val byteData = md.digest()
                
                // Convert to hex string
                val hexString = StringBuilder()
                for (byte in byteData) {
                    val hex = Integer.toHexString(0xff and byte.toInt())
                    if (hex.length == 1) hexString.append('0')
                    hexString.append(hex)
                }

                hexString.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                "Error"
            }

        /**
         * Retrieves license data from GitHub repository.
         * 
         * @return YAML content from the license registry
         */
        private fun getContentFromGithub(): String {
            val client = HttpClient.newBuilder().build()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://raw.githubusercontent.com/LiberaTeMetuMortis/Licence/main/licence.yml"))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            return response.body()
        }

        /**
         * Validates the current machine's license code against the remote registry.
         * 
         * @param plugin The plugin instance requesting license verification
         * @return true if the license is valid, false otherwise
         */
        fun parseYAMLAndCheckLicenceCode(plugin: JavaPlugin): Boolean {
            LoggingService.info("Plugin License Code: $licenceCode")
            
            try {
                val yamlString = getContentFromGithub()
                val parsedYAML = Yaml().load<Map<String, ArrayList<LinkedHashMap<String, String>>>>(yamlString)
                
                // Check if plugin is registered in the license registry
                val licensesForPlugin = parsedYAML[plugin.name] ?: return false
                
                // Check if the machine's license code is valid for this plugin
                return licensesForPlugin.any { it["licence_code"] == licenceCode }
            } catch (e: Exception) {
                LoggingService.severe("License verification failed: ${e.message}")
                return false
            }
        }
    }
} 