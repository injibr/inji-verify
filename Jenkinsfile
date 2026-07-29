automata {

    skipHom = true

    build.agent.image = 'library/maven:3.9-eclipse-temurin-21'

    gitOps.provider = 'GIT_INFRA'
    gitOps.engine = 'HELM'
    gitOps.repos = [
        dev: 'gitops-np/credenciais-verificaveis',
        hom: 'gitops-np/credenciais-verificaveis',
        prd: 'gitops-p/credenciais-verificaveis',
    ]

    containers.add descriptor: 'verify-service/Dockerfile', imageName: 'inji/inji-verify' , tagKey:'injiVerify.service.image.tag'

    artifacts.add file: 'verify-service/target/verify-service-${version}-sources.jar'

    build.opts = "-Dgpg.skip=true -Dmaven.javadoc.skip=true"

}
