podTemplate(yaml: """
kind: Pod
spec:
  containers:
  - name: jnlp
    image: gcr.io/manage-261115/jenkins-slave-tools:v1.2
    imagePullPolicy: Always
    tty: false
  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    imagePullPolicy: Always
    command:
    - /busybox/cat
    tty: true
    volumeMounts:
      - name: kaniko-secret
        mountPath: /secret
    env:
    - name: GOOGLE_APPLICATION_CREDENTIALS
      value: /secret/kaniko-secret.json
  volumes:
  - name: kaniko-secret
    secret:
      secretName: kaniko-secret
"""
) {
    node(POD_LABEL){
        container('jnlp') {
        def SONAR_PROJECT = "frontend"    
		def SONAR_URL = "http://sonar.mc1985.net"
		def AUTH = ""
       
 //       stage ("Artifact Build") {
 //           container('kaniko') {
 //            sh "/kaniko/executor --dockerfile=Dockerfile --context git://github.com/jc-benchmarkcorp/spring-petclinic --destination gcr.io/manage-261115/petclinic:latest"
 //     }
 //       }
        stage("Code Quality Scan") {
            container('jnlp') {
	            def response = httpRequest url: "${SONAR_URL}/api/measures/search_history?component=${SONAR_PROJECT}&metrics=bugs,code_smells,vulnerabilities,security_hotspots,alert_status", 
                    customHeaders: [[name: 'Authorization', 
                    value: "Basic ${AUTH}"]], 
                    ignoreSslErrors: true, 
                    httpMode: 'GET'
                status = readJSON(text: response.content)
                    }
              }
	     }
	     stage("Code Quality Results") {
            container('jnlp') {
             def SONAR_PROJECT = "frontend"
               echo """
                      Quality Gate Results for: ${SONAR_PROJECT}
                      -----------------------------------------
                      VULNERABILITIES:   ${status.measures[0].history[-1].value}
                      SECURITY_HOTSPOTS: ${status.measures[1].history[-1].value}
                      QUALITY_STATUS:    ${status.measures[4].history[-1].value}
                      CODE_SMELLS:       ${status.measures[3].history[-1].value}
                      BUGS:              ${status.measures[2].history[-1].value}
                      DATE:              ${status.measures[0].history[-1].date}
                    """
              sh  """
                curl --request POST \
  --url 'https://benchmarkcorp.atlassian.net/rest/api/3/issue' \
  --user 'matt.cole@benchmarkcorp.com:' \
  --header 'Accept: application/json' \
  --header 'Content-Type: application/json' \
  --data '{
"fields":{
  "project": {
    "key": "MD"
},
"summary": "SonarQube Results for frontend Jenkins-${BUILD_ID}",
"issuetype": {
    "id": "10010"    
},
"project": {
    "key": "MD"
},
"description": {
    "type": "doc",
    "version": 1,
    "content": [
        {
        "type": "paragraph",
        "content": [
            {
            "text": "Quality Gate Results for: ${SONAR_PROJECT}\\n-----------------------------------------\\nVULNERABILITIES:   ${status.measures[0].history[-1].value}\\nSECURITY_HOTSPOTS: ${status.measures[1].history[-1].value}\\nQUALITY_STATUS: ${status.measures[4].history[-1].value}\\nCODE_SMELLS: ${status.measures[3].history[-1].value}\\nBUGS: ${status.measures[2].history[-1].value}\\nDATE: ${status.measures[0].history[-1].date}",
            "type": "text"
            }
        ]
        }
    ]
    }
}
}'
"""
            }
	     }
		def AQUA_HOST = "http://aquasec.mc1985.net:8080";
		def AQUA_REGISTRY = "gcr";
		def REPOSITORY = "manage-261115/petclinic";
		def TAG = "latest";
        def IMAGE_NAME = "${REPOSITORY}:${TAG}";
        def USER_NAME = "administrator";
        def USER_PASSWORD = "";

        stage("Image Security Scan") {
              echo "Starting scan of ${REPOSITORY}:${TAG}"
              def message = "{\"id\": \"${USER_NAME}\", \"password\": \"${USER_PASSWORD}\"}";
              def login = httpRequest acceptType: 'APPLICATION_JSON', contentType: 'APPLICATION_JSON',
                    httpMode: 'POST',
                    requestBody: message, consoleLogResponseBody: true,
                    url: "${AQUA_HOST}/api/v1/login",
                    validResponseContent: 'ok'
	            def postRC = login.status
	            	    if(postRC.equals(200)) {

	            def sessionId = readJSON(text: login.content).token
	            def response = httpRequest url: "${AQUA_HOST}/api/v2/images/${AQUA_REGISTRY}/${REPOSITORY}/${TAG}", customHeaders: [[name: 'Authorization', value: "Bearer ${sessionId}"]], httpMode: 'GET'
	        		def status = readJSON text: response.content
                   while(status.scan_status == 'pending' || status.scan_status == 'in_progress') {
                   echo "Scanning in progress..."
                   sleep 2
                   response = httpRequest url: "${AQUA_HOST}/api/v2/images/${AQUA_REGISTRY}/${REPOSITORY}/${TAG}", customHeaders: [[name: 'Authorization', value: "Bearer ${sessionId}"]], httpMode: 'GET'
                   status = readJSON text: response.content
                   echo "Scan status: ${status.scan_status}"
              }

              if(status.scan_status != 'finished') {
              error("Image scan finished unsuccessfully. Image: ${REPOSITORY}:${TAG}")
              }

              if(status.disallowed) {
              error("Image scan completed, but image disallowed. Image: ${REPOSITORY}:${TAG}")
              }
              
              stage(" Image Scan Results: ${REPOSITORY}:${TAG}") {
              container('jnlp') {
               echo """
                    Image: ${REPOSITORY}:${TAG}
                    ------------------------------
                    CVETOTAL = ${status.vulns_found}
                    CVECRIT  = ${status.crit_vulns}
                    CVEHIGH  = ${status.high_vulns}
                    CVEMED   = ${status.med_vulns}
                    CVELOW   = ${status.low_vulns}
                    CVENEG   = ${status.neg_vulns}
                    """
                sh  """
                curl --request POST \
  --url 'https://benchmarkcorp.atlassian.net/rest/api/3/issue' \
  --user 'matt.cole@benchmarkcorp.com:' \
  --header 'Accept: application/json' \
  --header 'Content-Type: application/json' \
  --data '{
"fields":{
  "project": {
    "key": "MD"
},
"summary": "AquaScan Image Results for ${REPOSITORY}:${TAG} Jenkins-${BUILD_ID}",
"issuetype": {
    "id": "10010"    
},
"project": {
    "key": "MD"
},
"description": {
    "type": "doc",
    "version": 1,
    "content": [
        {
        "type": "paragraph",
        "content": [
            {
            "text": "AquaSec Image Scan Results for: ${REPOSITORY}:${TAG}\\n-----------------------------------------\\nCVETOTAL: ${status.vulns_found}\\nCVECRIT: ${status.crit_vulns}\\nCVEHIGH: ${status.high_vulns}\\nCVEMED: ${status.med_vulns}\\nCVELOW: ${status.low_vulns}\\nCVENEG: ${status.neg_vulns}",
            "type": "text"
            }
        ]
        }
    ]
    }
}
}'
"""
              }
              }
              stage("Performance signature")
            sh "git clone https://github.com/jc-benchmarkcorp/spring-petclinic.git"
            stage("Deploy JMeter")
            sh "kubectl apply -f ./spring-petclinic/jmeter/jmeter-deploy.yml"
            sleep 10
              stage("Dynatrace Deployment Event")
            createDynatraceDeploymentEvent(
                envId: 'Dynatrace Tenant',
               tagMatchRules :[
                   [
                       meTypes:  [[meType: "SERVICE"]],
                       tags: [[context: 'Kubernetes', key: 'app', value: 'petclinic']]
                       ]
                  ]) {
            stage("Application Deployment")
                sh "kubectl apply -f ./spring-petclinic/deployment.yaml"
            stage("Performance Check") 
           recordDynatraceSession(
               envId: 'Dynatrace Tenant',
               testCase: 'Perf-test',
               tagMatchRules :[
                   [
                       meTypes:  [[meType: "SERVICE"]],
                       tags: [[context: 'Kubernetes', key: 'app', value: 'petclinic']]
                       ]
                  ]) {
          
          sh  '''
                 jmeter_pod=$(kubectl get pod -n jmeter -o jsonpath="{.items[0].metadata.name}")
                 kubectl cp ./spring-petclinic/jmeter/petclinic.jmx $jmeter_pod:/ -n jmeter
                 kubectl -n jmeter exec -i $jmeter_pod -- /bin/bash /opt/apache-jmeter-5.3/bin/jmeter -n -t ../../petclinic.jmx -JTHREADS=100 -JLOOP_COUNT=500
              '''
              }   
        perfSigDynatraceReports envId: 'Dynatrace Tenant', 
        nonFunctionalFailure: 1, 
        specFile: "./spring-petclinic/pet_perfsig.json"
                  }
                        
              stage("Secuirty Gates") {
              echo "Starting scan of ${REPOSITORY}:${TAG}"
                def SONAR_PROJECT = "frontend" 
                def SONAR_URL = "http://sonar.mc1985.net"
                def AUTH = ""
	            def getresults = httpRequest url: "${AQUA_HOST}/api/v1/scanner/registry/${AQUA_REGISTRY}/image/${REPOSITORY}:${TAG}/scan_result", customHeaders: [[name: 'Authorization', value: "Bearer ${sessionId}"]], httpMode: 'GET'
	        		def results = readJSON text: getresults.content
                     if(results.disallowed != 'true') {
                     echo "${REPOSITORY}:${TAG} has PASSED vulnerability and Policy Checks"
                     }
                     else(results.disallowed = 'true') {
                     error("Image scan completed, but image disallowed. Image: ${REPOSITORY}:${TAG}")
                     }
                     container('jnlp') {
	            def qualitygate = httpRequest url: "${SONAR_URL}/api/measures/search_history?component=${SONAR_PROJECT}&metrics=bugs,code_smells,vulnerabilities,security_hotspots,alert_status", 
                    customHeaders: [[name: 'Authorization', 
                    value: "Basic ${AUTH}"]], 
                    ignoreSslErrors: true, 
                    httpMode: 'GET'
                qualitystatus = readJSON(text: qualitygate.content)
                    }
                    if(qualitystatus.measures[2].history[-1].value != "OK") {
                    unstable("Quality Gates Status of ERROR")
                     }
                    else(qualitystatus.measures[2].history[-1].value = "ERROR") {
                     echo "Quality Gates Status of OK"
                     }
              }
              stage('Promote to Prod?') {
           timeout(time:12, unit:'HOURS') {
              input message:'Approve promotion?', submitter: 'sre-approvers'
           }
         }
	            	    }
      }
    }
    }
  
