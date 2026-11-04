[CmdletBinding()]
param()

$volume = 'saver_objectdata'
Write-Warning "Esto detendrá el entorno Docker y eliminará exclusivamente el volumen '$volume' con objetos multimedia de desarrollo."
Write-Host "No elimina el volumen PostgreSQL 'saver_pgdata' ni archivos del host."
$answer = Read-Host "Escribí ELIMINAR para continuar"
if ($answer -ne 'ELIMINAR') {
    Write-Host 'Operación cancelada; no se eliminó ningún dato.'
    exit 0
}

docker compose down
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$exists = docker volume ls --format '{{.Name}}' | Where-Object { $_ -eq $volume }
if ($exists) {
    docker volume rm $volume
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host "Eliminado: $volume"
} else {
    Write-Host "El volumen $volume no existe; no había objetos multimedia para eliminar."
}
