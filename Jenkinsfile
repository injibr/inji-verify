automata {

    def version = '2.0.0'

    descriptor = "groupId=inji,artifactId=inji-verify,version=${version}"
    skipHom = true

    gitOps.provider = 'GIT_INFRA'     
    gitOps.namespace = 'inji'     
    gitOps.repos = [dev: 'gitops-np/inji']

    containers.add descriptor: 'verify-service/Dockerfile', imageName: 'inji/inji-verify'

    qa.sonarOpts = "-Dsonar.projectKey=br.gov.dataprev.inji:inji-verify -Dsonar.projectVersion=${version} -Dsonar.sources=."
    qa.encoding = 'UTF-8'

}
