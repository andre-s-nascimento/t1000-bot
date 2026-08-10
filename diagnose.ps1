Write-Host "Diretorio atual: $(Get-Location)"
Write-Host ""
Write-Host "Arquivos Java encontrados:"
$javaFiles = Get-ChildItem -Recurse -Filter "*.java" -File
Write-Host "Total: $($javaFiles.Count)"
foreach ($f in $javaFiles | Select-Object -First 3) {
    Write-Host "  $($f.FullName)"
}
Write-Host ""
Write-Host "Arquivos SH encontrados:"
$shFiles = Get-ChildItem -Recurse -Filter "*.sh" -File
Write-Host "Total: $($shFiles.Count)"
foreach ($f in $shFiles | Select-Object -First 3) {
    Write-Host "  $($f.FullName)"
}
