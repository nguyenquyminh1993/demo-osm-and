# PowerShell script to copy icons from websource to assets folder
# Run this script from the project root directory

$sourceDir = "C:\Users\2NF\Downloads\websource\icon"
$targetDir = "app\src\main\assets\icons"

# Required icon files
$requiredIcons = @(
    "hotel.png",
    "restaurant.png",
    "facility.png",
    "shop.png",
    "beach.png",
    "lift.png",
    "golf.png",
    "parking.png",
    "chapel.png"
)

Write-Host "Copying icons from $sourceDir to $targetDir..." -ForegroundColor Cyan

# Create target directory if it doesn't exist
if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
    Write-Host "Created directory: $targetDir" -ForegroundColor Green
}

# Copy each icon file
$copiedCount = 0
$missingCount = 0

foreach ($icon in $requiredIcons) {
    $sourcePath = Join-Path $sourceDir $icon
    $targetPath = Join-Path $targetDir $icon
    
    if (Test-Path $sourcePath) {
        Copy-Item -Path $sourcePath -Destination $targetPath -Force
        Write-Host "Copied: $icon" -ForegroundColor Green
        $copiedCount++
    } else {
        Write-Host "Missing: $icon" -ForegroundColor Yellow
        $missingCount++
    }
}

Write-Host ""
Write-Host "Copy completed!" -ForegroundColor Cyan
Write-Host "Copied: $copiedCount files" -ForegroundColor Green
if ($missingCount -gt 0) {
    Write-Host "Missing: $missingCount files" -ForegroundColor Yellow
    Write-Host "Please check if icons exist in source directory" -ForegroundColor Yellow
}
