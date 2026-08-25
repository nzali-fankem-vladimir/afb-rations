<#
    Cree (ou recree) le realm de developpement afb-rations-dev dans un conteneur Keycloak.

    Le module ne gere aucun mot de passe applicatif : ce script ne fait qu'alimenter
    le fournisseur d'identite. Voir CLAUDE.md section 10.

    Le realm est heberge dans le conteneur Keycloak partage du poste de developpement.
    Keycloak en mode start-dev ne persiste pas apres suppression du conteneur :
    ce script, avec realm-afb-rations-dev.json, est le moyen de le recreer.

    Exemples :
        .\init-realm.ps1
        .\init-realm.ps1 -Force                     # recree le realm s'il existe deja
        .\init-realm.ps1 -Container keycloak-rations -AdminPassword admin
#>
param(
    [string]$Container     = "dottel-keycloak",
    [string]$AdminUser     = "admin",
    # Mot de passe de la console d'administration du conteneur Keycloak.
    # Aucun mot de passe reel n'est versionne : renseigner KEYCLOAK_ADMIN_PASSWORD
    # dans la session, ou passer -AdminPassword. Le repli correspond a la valeur
    # du guide de preparation d'environnement.
    [string]$AdminPassword = $(if ($env:KEYCLOAK_ADMIN_PASSWORD) { $env:KEYCLOAK_ADMIN_PASSWORD } else { "admin" }),
    [string]$Realm         = "afb-rations-dev",
    [switch]$Force
)

$ErrorActionPreference = "Stop"
$kcadm       = "/opt/keycloak/bin/kcadm.sh"
$realmFile   = Join-Path $PSScriptRoot "realm-$Realm.json"
$containerIn = "/tmp/realm-$Realm.json"

if (-not (Test-Path $realmFile)) { throw "Fichier de realm introuvable : $realmFile" }

Write-Host "1/4 Copie de la definition du realm dans $Container"
docker cp $realmFile "${Container}:${containerIn}"
if (-not $?) { throw "Conteneur $Container injoignable. Est-il demarre ?" }

Write-Host "2/4 Authentification sur la console d'administration"
docker exec $Container $kcadm config credentials --server http://localhost:8080 --realm master --user $AdminUser --password $AdminPassword
if (-not $?) { throw "Authentification refusee sur le realm master." }

Write-Host "3/4 Verification de l'existence du realm $Realm"
$ErrorActionPreference = "Continue"
$realmsConnus = docker exec $Container $kcadm get realms --fields realm
$ErrorActionPreference = "Stop"
$motifRealm = [regex]::Escape('"' + $Realm + '"')
if ($realmsConnus -match $motifRealm) {
    if (-not $Force) {
        Write-Host "Le realm $Realm existe deja. Relancer avec -Force pour le recreer." -ForegroundColor Yellow
        exit 0
    }
    Write-Host "    suppression du realm existant (-Force)"
    docker exec $Container $kcadm delete "realms/$Realm"
}

Write-Host "4/4 Creation du realm, du client public, des six roles et des six comptes de test"
docker exec $Container $kcadm create realms -f $containerIn
if (-not $?) { throw "Creation du realm $Realm en echec." }

Write-Host ""
Write-Host "Realm $Realm cree." -ForegroundColor Green
Write-Host "  Issuer  : http://localhost:8180/realms/$Realm"
Write-Host "  Client  : rations-frontend (public, authorization code + PKCE S256)"
Write-Host "  Comptes : jean_mbarga, paul_essama, sylvie_atangana, claire_nkolo, agnes_tchinda, martin_fouda"
