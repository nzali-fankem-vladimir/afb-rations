# Verifie que le conteneur Keycloak partage (dottel-keycloak) repond et que le
# realm afb-rations-dev y existe, avant de lancer les services applicatifs.
#
# Ce script ne demarre et ne gere rien : dottel-keycloak appartient au projet
# DOTTEL, pas a ce depot (voir README.md de ce dossier). Il se contente de
# signaler l'etat et de rappeler la commande a executer en cas de probleme.

param(
    [string]$RealmUrl = "http://localhost:8180/realms/afb-rations-dev"
)

Write-Host "Verification de Keycloak ($RealmUrl)..."

try {
    $reponse = Invoke-WebRequest -Uri $RealmUrl -UseBasicParsing -TimeoutSec 5
    if ($reponse.StatusCode -eq 200) {
        Write-Host "OK : dottel-keycloak repond et le realm afb-rations-dev existe." -ForegroundColor Green
        exit 0
    }
}
catch [System.Net.WebException] {
    $reponseHttp = $_.Exception.Response
    if ($reponseHttp -and $reponseHttp.StatusCode -eq 404) {
        Write-Host "ATTENTION : Keycloak repond mais le realm afb-rations-dev est absent." -ForegroundColor Yellow
        Write-Host "Recree-le avec : .\init-realm.ps1" -ForegroundColor Yellow
        exit 1
    }

    Write-Host "ATTENTION : le conteneur dottel-keycloak ne repond pas sur localhost:8180." -ForegroundColor Yellow
    Write-Host "Demarre-le depuis le projet DOTTEL avant de lancer les services applicatifs d'afb-rations." -ForegroundColor Yellow
    exit 1
}
