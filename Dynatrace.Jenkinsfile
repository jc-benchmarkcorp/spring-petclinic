podTemplate(
   containers: [
      containerTemplate(
              name: 'jnlp',
              image: 'yonadev/jnlp-slave-k8s-helm',
              alwaysPullImage: true,
              args: '${computer.jnlpmac} ${computer.name}',
              ttyEnabled: false,
              command: '',
              resourceRequestCpu: '1000m',
              resourceLimitCpu: '2000m',
              resourceRequestMemory: '1000Mi',
              resourceLimitMemory: '2000Mi')
    ]) {
        node(POD_LABEL) {
        container('jnlp') {
        
            stage("Git Pull")
            sh "git clone https://github.com/jc-benchmarkcorp/spring-petclinic.git"
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
        }
        }
        }
