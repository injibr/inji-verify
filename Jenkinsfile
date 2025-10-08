automata {

    skipHom = true

    build.agent.image = 'library/maven:3.9-eclipse-temurin-21'

    //kustomization not ready
    //gitOps.provider = 'GIT_INFRA'     
    //gitOps.namespace = 'inji'     
    //gitOps.repos = [dev: 'gitops-np/inji']

    containers.add descriptor: 'verify-service/Dockerfile', imageName: 'inji/inji-verify'

    artifacts.add file: 'verify-service/target/inji-verify-${version}.jar'

    build.opts = "-Dgpg.skip=true -Dmaven.javadoc.skip=true"

}
