automata {
    // Parâmetros gerais
    type = 'CUSTOM'
    def version = '1.0.0'

    descriptor = "groupId=inji,artifactId=inji-verify,version=${version}"
    containers.add descriptor: 'Dockerfile', imageName: 'inji/inji-verify'

    qa.sonarOpts = "-Dsonar.projectKey=br.gov.dataprev.inji:inji-verify -Dsonar.projectVersion=${version} -Dsonar.sources=."
    qa.encoding = 'UTF-8'
}
