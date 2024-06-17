import com.kms.katalon.core.annotation.AfterTestCase
import com.kms.katalon.core.annotation.AfterTestSuite
import com.kms.katalon.core.annotation.BeforeTestCase
import com.kms.katalon.core.annotation.BeforeTestSuite
import com.kms.katalon.core.configuration.RunConfiguration
import com.kms.katalon.core.context.TestCaseContext
import com.kms.katalon.core.context.TestSuiteContext
import com.kms.katalon.core.cucumber.keyword.CucumberBuiltinKeywords as CucumberKW
import com.kms.katalon.core.exception.StepFailedException
import com.kms.katalon.core.logging.KeywordLogger
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import groovy.io.FileType
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.swing.factory.ComboBoxFactory
import internal.GlobalVariable
import io.netty.util.concurrent.GlobalEventExecutor
import java.text.ParseException;

import com.ari.utils.TestResult
import java.util.Locale
import java.util.TimeZone
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import groovy.json.JsonOutput
import com.ari.utils.GlobalPayLoad
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.Path

class ARITestListener {
	// Objet qui gère les résultats de test pour créer le tableau final dans l'exécution
	static List<TestResult> testResults = new ArrayList<>()
	// Fonction qui optimise l'affichage dans le rapport Github
	static def void printkv(String name, Object value) {
	    def finalName = name.replaceAll(~"^GlobalVariable\\.", "").replaceAll(~"\\(\\)\$", "")
	    def paddingLen = 40
	    def paddingStr = "." * paddingLen
	    def paddedKey = "${finalName} ${paddingStr}"[0..paddingLen]
	    
	    def simpleTypes = [Byte, Short, Integer, Long, Double, Float, Boolean, String]
	
	    if (simpleTypes.any { it.isInstance(value) }) {
	        println("$paddedKey : [$value]")
	    } else {
	        def finalValue = new JsonBuilder(value).toPrettyString()
	        println("$paddedKey : $finalValue")
	    }
	}
 
	/**
	 * Executes before every test suite starts.
	 * @param testSuiteContext: related information of the executed test suite.
	 */
	@BeforeTestSuite
	def beforeTestSuite(TestSuiteContext testSuiteContext) {
	    KeywordLogger log = new KeywordLogger()
	
	    if (GlobalVariable.G_activateListener == null || !"true".equals(GlobalVariable.G_activateListener)) {
	        log.logInfo("G_activateListener is not set to true. Exiting beforeTestSuite.")
	        return
	    }
	
	    log.logInfo("===== Info Log|BeforeTestSuite =====")
	    printkv("testSuiteContext", testSuiteContext)
	
	    String testSuiteId = testSuiteContext.getTestSuiteId()
	    String testSuiteIdDisplay = testSuiteId.substring(testSuiteId.lastIndexOf("/") + 1)
	    String projectDir = RunConfiguration.getProjectDir()
	    String location = "$projectDir/$testSuiteId.ts"
	    File testSuiteFile = new File(location)
	
	    if (!testSuiteFile.exists()) {
	        log.logError("Test suite file not found at location: $location")
	        throw new StepFailedException("Test suite file not found at location: $location")
	    }
	
	    String xmlText = testSuiteFile.text
	    def testList = new XmlSlurper().parseText(xmlText)
	    def list = testList.testCaseLink.testCaseId.collect { it.toString().substring(it.lastIndexOf("/") + 1) }
	    
	    GlobalVariable.g_jira_testCaseIdList = list
	    assert GlobalVariable.g_jira_testCaseIdList != null : "Failed to create g_jira_testCaseIdList"
	    printkv("g_jira_testCaseIdList", GlobalVariable.g_jira_testCaseIdList)
	
	    def profileName = RunConfiguration.getExecutionProfile()
	    GlobalVariable.g_jira_summary = "Automation Test Execution : $testSuiteIdDisplay - $profileName"
	    assert GlobalVariable.g_jira_summary != null : "Failed to create g_jira_summary"
	    printkv("lien avec le profile:", GlobalVariable.g_jira_summary)
	    printkv("g_jira_testExecutionIssueTypeId", GlobalVariable.g_jira_testExecutionIssueTypeId)
	
	    try {
	        GlobalVariable.g_jira_projectId = CustomKeywords.'com.ari.utils.ARIRestServices.jira_getProjectId'()
	        assert GlobalVariable.g_jira_projectId != null : "Failed to get Jira Project Id"
	        printkv("g_jira_projectId", GlobalVariable.g_jira_projectId)
	    } catch (Exception e) {
	        log.logError("Failed to get Jira Project Id: $e.message")
	        throw new StepFailedException("Failed to get Jira Project Id: $e.message", e)
	    }
	
	    try {
	        GlobalVariable.g_jira_testExecutionKey = CustomKeywords.'com.ari.utils.ARIRestServices.jira_createTestexecution'(
	            GlobalVariable.g_jira_summary,
	            GlobalVariable.g_jira_testExecutionIssueTypeId,
	            GlobalVariable.g_jira_projectId
	        )
	        assert GlobalVariable.g_jira_testExecutionKey != null : "Failed to create Jira Test Execution"
	        CustomKeywords.'com.ari.utils.ARIRestServices.xray_execution_parametize'()
	        printkv("G_xray_testPlanKey", GlobalVariable.G_xray_testPlanKey)
	        printkv("g_jira_testExecutionKey", GlobalVariable.g_jira_testExecutionKey)
	    } catch (Exception e) {
	        log.logError("Failed to create Jira Test Execution: $e.message")
	        throw new StepFailedException("Failed to create Jira Test Execution: $e.message", e)
	    }
	
	    try {
	        CustomKeywords.'com.ari.utils.ARIRestServices.jira_changeIssueJiraTransition'(GlobalVariable.g_jira_testExecutionKey)
	        log.logInfo("Jira issue transition changed successfully")
	    } catch (Exception e) {
	        log.logError("Failed to change Jira issue transition: $e.message")
	        throw new StepFailedException("Failed to change Jira issue transition: $e.message", e)
	    }
	
	    try {
	        def (projectName, testExecutionId, testRunOrder, testRunWebUrl) = CustomKeywords.'com.ari.utils.ARIRestServices.katalon_searchLatestExecution'()
	        GlobalVariable.g_katalon_projectName = projectName
	        GlobalVariable.g_katalon_testExecutionid = testExecutionId
	        GlobalVariable.g_katalon_testRunOrder = testRunOrder
	        GlobalVariable.g_katalon_testRunWebUrl = testRunWebUrl
	        assert GlobalVariable.g_katalon_projectName != null : "Failed to get Katalon project name"
	        assert GlobalVariable.g_katalon_testExecutionid != null : "Failed to get Katalon test execution id"
	        assert GlobalVariable.g_katalon_testRunOrder != null : "Failed to get Katalon test run order"
	        assert GlobalVariable.g_katalon_testRunWebUrl != null : "Failed to get Katalon test run web URL"
	        printkv("g_katalon_projectName", GlobalVariable.g_katalon_projectName)
	        printkv("g_katalon_testExecutionid", GlobalVariable.g_katalon_testExecutionid)
	        printkv("g_katalon_testRunOrder", GlobalVariable.g_katalon_testRunOrder)
	        printkv("g_katalon_testRunWebUrl", GlobalVariable.g_katalon_testRunWebUrl)
	    } catch (Exception e) {
	        log.logError("Failed to search latest Katalon execution: $e.message")
	        throw new StepFailedException("Failed to search latest Katalon execution: $e.message", e)
	    }
	
	    if (GlobalVariable.g_katalon_testExecutionid && GlobalVariable.g_katalon_testRunWebUrl) {
	        try {
	            GlobalVariable.g_katalon_testRunTitle = "Katalon TestRun ${testSuiteIdDisplay}#${GlobalVariable.g_katalon_testRunOrder} du projet ${GlobalVariable.g_katalon_projectName}"
	            CustomKeywords.'com.ari.utils.ARIRestServices.jira_createRemoteIssueLink'(
	                GlobalVariable.g_jira_testExecutionKey,
	                GlobalVariable.g_katalon_testRunTitle,
	                GlobalVariable.g_katalon_testRunWebUrl
	            )
	            log.logInfo("Jira remote issue link created successfully")
	        } catch (Exception e) {
	            log.logError("Failed to create Jira remote issue link: $e.message")
	            throw new StepFailedException("Failed to create Jira remote issue link: $e.message", e)
	        }
	    }
	}	
	/**
	 * Executes before every test case starts.
	 * @param testCaseContext related information of the executed test case.
	 */
	@BeforeTestCase
	def beforeTestCase(TestCaseContext testCaseContext) {
	    CucumberKW.GLUE = ['common']
	
	    // Vérifier si les listeners sont activés. Si non, sortir de la fonction.
	    if (GlobalVariable.G_activateListener == null || !"true".equals(GlobalVariable.G_activateListener)) {
	        return
	    }
	    
	    // Affichage d'informations de début de test
	    println("===== Info Log|BeforeTestCase =====")
	    printkv("testCaseContext", testCaseContext)
	    
	    // Récupération et formatage de l'ID du cas de test pour une utilisation ultérieure
	    String testCaseId = testCaseContext.getTestCaseId()
	    GlobalVariable.g_testCaseKey = testCaseId.substring(testCaseId.lastIndexOf("/") + 1)
	    printkv("g_testCaseKey", GlobalVariable.g_testCaseKey)
	    
	    // Enregistrement de l'heure de début du test
	    GlobalVariable.g_startTestTime = CustomKeywords.'com.ari.utils.ARIRestServices.currentDateTime'()
	    printkv("g_startTestTime", GlobalVariable.g_startTestTime)
	}


	/**
	 * Executes after every test case ends.
	 * @param testCaseContext related information of the executed test case.
	 */
	@AfterTestCase
	def afterTestCase(TestCaseContext testCaseContext) {
	    if (GlobalVariable.G_activateListener == null || !"true".equals(GlobalVariable.G_activateListener)) {
	        return
	    }
	
	    // Affichage d'informations de fin de test
	    println("===== Info Log|AfterTestCase =====")
	    printkv("testCaseContext", testCaseContext)
	
	    // Enregistrement de l'heure de fin du test
	    GlobalVariable.g_finishTestTime = CustomKeywords.'com.ari.utils.ARIRestServices.currentDateTime'()
	
	    // Évaluation du statut du test et attribution d'un commentaire approprié
	    def status = testCaseContext.getTestCaseStatus()
	    switch(status) {
	        case "PASSED":
	            GlobalVariable.g_testStatus = "PASSED"
	            GlobalVariable.g_testComment = "Execution terminée avec succès"
	            break
	        case ["ERROR", "FAILED"]:
	            GlobalVariable.g_testStatus = "FAILED"
	            GlobalVariable.g_testComment = "Execution en échec"
	            break
	        case "SKIPPED":
	            GlobalVariable.g_testStatus = "ABORTED"
	            GlobalVariable.g_testComment = "Execution ignorée"
	            break
	        default:
	            throw new Exception("unknown status $status")
	    }
	
	    // Ajoute un lien vers le TestRun sur Katalon TestOps si disponible
	    if (GlobalVariable.g_katalon_testRunWebUrl) {
	        GlobalVariable.g_testComment += "\n[Lien vers le TestRun #${GlobalVariable.g_katalon_testRunOrder}|${GlobalVariable.g_katalon_testRunWebUrl}] sur Katalon TestOps"
	    }
	
	    // Formatage du commentaire pour le rapport
	    GlobalVariable.g_testComment = GlobalVariable.g_testComment.replaceAll("\n", "\\\\n").replaceAll("\r", "\\\\r").replaceAll("\t", "\\\\t")
	
	    // Log des informations liées à Xray et JIRA
	    printkv("G_xray_clientId", GlobalVariable.G_xray_clientId)
	    printkv("G_xray_clientSecret", GlobalVariable.G_xray_clientSecret)
	    GlobalVariable.g_xray_accessToken = CustomKeywords.'com.ari.utils.ARIRestServices.xray_authenticate'()
	    printkv("g_xray_accessToken", GlobalVariable.g_xray_accessToken)
	
	    // Affichage d'autres variables globales pertinentes
	    ["G_xray_testPlanKey", "g_jira_testExecutionKey", "g_testCaseKey", "g_testStatus", "g_testComment", "g_startTestTime", "g_finishTestTime"].each { var ->
	        printkv(var, GlobalVariable."$var")
	    }
	
	    // Définir un Locale spécifique pour éviter les problèmes liés aux paramètres régionaux du système
	    Locale.setDefault(Locale.US)
	    def dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").with {
	        it.setTimeZone(TimeZone.getTimeZone("UTC"))
	        it
	    }
	
	    def timestamp_start, timestamp_end
	    try {
	        timestamp_start = dateFormat.parse(GlobalVariable.g_startTestTime).time
	        timestamp_end = dateFormat.parse(GlobalVariable.g_finishTestTime).time
	    } catch (ParseException e) {
	        println "Erreur de parsing de date: ${e.message}"
	    }
	
	    // Ajout des résultats de test à la structure du rapport Xray
	    println('------ Ajout du test à la structure du rapport XRAY ------')
	    GlobalPayLoad.globalPayload.tests.add([
	        testKey: GlobalVariable.g_testCaseKey,
	        start: GlobalVariable.g_startTestTime,
	        finish: GlobalVariable.g_finishTestTime,
	        comment: GlobalVariable.g_testComment,
	        status: GlobalVariable.g_testStatus
	    ])
	
	    // Mise à jour des résultats de test avec les données collectées
	    println("=>Update Creation with Table")
	    if ("FAILED".equals(GlobalVariable.g_testStatus)) {
	        testResults.add(new TestResult(
	            xrayTestPlanKey: GlobalVariable.G_xray_testPlanKey,
	            jiraTestExecutionKey: GlobalVariable.g_jira_testExecutionKey,
	            testCaseKey: GlobalVariable.g_testCaseKey,
	            testStatus: GlobalVariable.g_testStatus,
	            testComment: GlobalVariable.g_testComment,
	            startTestTime: timestamp_start,
	            finishTestTime: timestamp_end
	        ))
	    }
	}

	// Fonction pour ajouter un tag à chaque cucumber.json avec le numéro de l exécution 
	def void addTagToFile(File file, String executionNumber) {
	    // Lire le contenu du fichier
	    String content = file.text
	    JsonSlurper slurper = new JsonSlurper()
	    List reports = slurper.parseText(content)
	
	    // Ajoute le tag à chaque élément de rapport
	     reports.each { report ->
        report.tags = report.tags ?: []
        report.tags.add(0, [ // Ajoute le tag au début de la liste
            name: "@${executionNumber}",
            type: "Tag",
            location: [line: 1, column: 1]
	        ])
	    }
		
	    // Réécrire le fichier avec le nouveau contenu
	    String modifiedContent = JsonOutput.toJson(reports)
	    Files.write(Paths.get(file.absolutePath), modifiedContent.getBytes())
	}
	
	// Fonction pour trouver récursivement tous les fichiers cucumber.json si ils sont présent et mettre la variable globale cucumber à true
	def List<File> findCucumberFiles(File dir) {
		    List<File> cucumberFiles = []
		    File[] files = dir.listFiles()
		    if (files != null) {
		        files.each { file ->
		            if (file.isDirectory()) {
		                cucumberFiles.addAll(findCucumberFiles(file))
		            } else if (file.name.endsWith("cucumber.json")) {
						addTagToFile(file, GlobalVariable.g_jira_testExecutionKey)
		                cucumberFiles.add(file)
						GlobalVariable.g_test_cucumber_file =true
		            }
		        }
		    }
		    return cucumberFiles
		}
	/**
	* Executes after every test suite ends.
	*/
	@AfterTestSuite
	def afterTestSuite(TestSuiteContext testSuiteContext) {
	    // Vérifie si les listeners sont activés. Si non, sort de la fonction.
	    if (GlobalVariable.G_activateListener == null || !"true".equals(GlobalVariable.G_activateListener)) {
	        return
	    }
	
	    // Affichage d'informations de fin de suite de tests
	    println("===== Info Log|AfterTestSuite =====")
	    printkv("testSuiteContext", testSuiteContext)
	
	    String reportFolder = RunConfiguration.getReportFolder() + "/cucumber_report"
	    Path outputPath = Paths.get(RunConfiguration.getProjectDir() + "/file_merged/merged_cucumber.json")
	
	    // Trouve tous les fichiers cucumber.json dans le dossier et sous-dossiers
	    List<File> allCucumberFiles = findCucumberFiles(new File(reportFolder))
	
	    if (GlobalVariable.g_test_cucumber_file) {
	        try {
	            // Concaténer le contenu des fichiers
	            StringBuilder mergedContent = new StringBuilder("[")
	            allCucumberFiles.eachWithIndex { file, index ->
	                String content = file.text.trim()
	                if (content.startsWith("[")) content = content.substring(1)
	                if (content.endsWith("]")) content = content.substring(0, content.length() - 1)
	                if (content.length() > 0) {
	                    if (index > 0) mergedContent.append(",")
	                    mergedContent.append(content)
	                }
	            }
	
	            println("on est dans le cucumber")
	            mergedContent.append("]")
	
	            // Imprimer le contenu fusionné
	            println("Contenu fusionné : \n${mergedContent.toString()}")
	
	            // Convertir en JSON formaté
	            def json = new JsonSlurper().parseText(mergedContent.toString())
	            String prettyJson = JsonOutput.prettyPrint(JsonOutput.toJson(json))
	
	            // Écrire le contenu fusionné dans le fichier de sortie
	            Files.write(outputPath, prettyJson.getBytes())
	
	            // Envoi du rapport fusionné à Xray
	            String bearerToken = GlobalVariable.g_xray_accessToken
	            String urlcucumber = "https://xray.cloud.getxray.app/api/v2/import/execution/cucumber"
	            String jsonFilePath = RunConfiguration.getProjectDir() + "/file_merged/merged_cucumber.json"
	
	            // Lire le contenu du fichier JSON
	            File file = new File(jsonFilePath)
	            String content = file.text
	
	            // Créer et configurer la connexion
	            URL urlObject = new URL(urlcucumber)
	            HttpURLConnection cucumberconnect = (HttpURLConnection) urlObject.openConnection()
	            cucumberconnect.setRequestMethod("POST")
	            cucumberconnect.setRequestProperty("Content-Type", "application/json")
	            cucumberconnect.setRequestProperty("Authorization", "Bearer " + bearerToken)
	            cucumberconnect.setDoOutput(true)
	
	            // Envoyer les données
	            OutputStreamWriter writer_cucumber = new OutputStreamWriter(cucumberconnect.getOutputStream())
	            writer_cucumber.write(content)
	            writer_cucumber.flush()
	            writer_cucumber.close()
	
	            // Logger la fin de l'envoi
	            println "Données envoyées. En attente de réponse..."
	
	            // Lire la réponse
	            int responseCode = cucumberconnect.responseCode
	            println "Code de réponse HTTP : $responseCode"
	            if (responseCode == HttpURLConnection.HTTP_OK) {
	                // Lire la réponse du serveur
	                String response = cucumberconnect.inputStream.text
	                println "Réponse du serveur : $response"
	            } else {
	                println "Réponse d'erreur du serveur : ${cucumberconnect.errorStream.text}"
	            }
	        } catch (FileNotFoundException e) {
	            println "Erreur : Fichier non trouvé - ${e.message}"
	        } catch (IOException e) {
	            println "Erreur IO : Problème lors de la lecture/écriture des fichiers - ${e.message}"
	        } catch (Exception e) {
	            println "Erreur inattendue : ${e.message}"
	        }
	    } else {
	        try {
	            // Préparation du payload pour Xray
	            GlobalPayLoad.globalPayload.testExecutionKey = GlobalVariable.g_jira_testExecutionKey
	            GlobalPayLoad.globalPayload.info.put("testPlanKey", GlobalVariable.G_xray_testPlanKey)
	            println "on est dans le normal"
	
	            // Conversion du payload en chaîne JSON
	            def jsonPayload = JsonOutput.toJson(GlobalPayLoad.globalPayload)
	
	            // Configuration et envoi de la requête HTTP POST à Xray
	            URL url = new URL("https://xray.cloud.getxray.app/api/v2/import/execution")
	            HttpURLConnection connection = (HttpURLConnection) url.openConnection()
	            connection.setRequestMethod("POST")
	            connection.setRequestProperty("Content-Type", "application/json")
	            connection.setRequestProperty("Authorization", "Bearer " + GlobalVariable.g_xray_accessToken)
	            connection.setDoOutput(true)
	
	            // Envoi du payload JSON
	            connection.outputStream.withWriter("UTF-8") { writer ->
	                writer.write(jsonPayload)
	            }
	
	            // Lecture et affichage de la réponse de Xray
	            def response2 = connection.inputStream.withReader("UTF-8") { reader ->
	                reader.text
	            }
	            println "Response: $response2"
	
	            // Fermeture de la connexion HTTP
	            connection.disconnect()
	        } catch (MalformedURLException e) {
	            println "Erreur URL : URL mal formée - ${e.message}"
	        } catch (IOException e) {
	            println "Erreur IO : Problème de connexion réseau ou lors de la lecture/écriture des données - ${e.message}"
	        } catch (Exception e) {
	            println "Erreur inattendue : ${e.message}"
	        }
	    }
	
	    new File("finished_suites/${testSuiteContext.getTestSuiteId()}.txt").write("done")
	
	    // Création du tableau JIRA pour les tests en erreur de la suite de tests
	    println("===== Create JIRA TABLE Description | API =====")
	    GlobalVariable.g_request_value = CustomKeywords.'com.ari.utils.ARIRestServices.jira_createTableExecution'(testResults)
	    println(GlobalVariable.g_request_value)
	
	    // Vérifie si la liste testResults contient des éléments
	    if (!testResults.isEmpty()) {
	        // Mise à jour de la description de la table JIRA avec les résultats de la suite de tests
	        println("===== Update JIRA TABLE Description of ${GlobalVariable.g_jira_testExecutionKey} | API =====")
	        def response = CustomKeywords.'com.ari.utils.ARIRestServices.jira_putTableExecution'()
	        println(response)
	    } else {
	        // Affiche un message si aucune instance de TestResult n'a été générée
	        println("Aucun Test en erreur")
	    }
	}
		
}
