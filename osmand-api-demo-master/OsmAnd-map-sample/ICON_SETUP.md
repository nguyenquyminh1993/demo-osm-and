# Icon Setup Instructions

## Overview
This project uses custom facility markers based on icons from `miyako_map.html`. The icons need to be copied to the assets folder.

## Required Icons

Copy the following icon files from `c:\Users\2NF\Downloads\websource\icon\` to `app\src\main\assets\icons\`:

- `hotel.png`
- `restaurant.png`
- `facility.png`
- `shop.png`
- `beach.png`
- `lift.png`
- `golf.png`
- `parking.png`
- `chapel.png`

## Icon Mapping

The `FacilityMarkerLayer` maps facility kinds to icons as follows:

| Facility Kind | Icon File | Size |
|--------------|----------|------|
| `hotel`, `inn` | `hotel.png` | 20x25 |
| `restaurant` | `restaurant.png` | 20x25 |
| `facility` | `facility.png` | 20x25 |
| `shop`, `shopping` | `shop.png` | 20x25 |
| `beach`, `swim` | `beach.png` | 20x25 |
| `lift` | `lift.png` | 20x25 |
| `golf` | `golf.png` | 20x25 |
| `parking` | `parking.png` | 15x18 |
| `chapel` | `chapel.png` | 20x25 |
| `amusement` | `facility.png` (fallback) | 20x25 |

## Setup Steps

1. Copy all icon files from `c:\Users\2NF\Downloads\websource\icon\` to `app\src\main\assets\icons\`
2. Ensure the folder structure is: `app\src\main\assets\icons\*.png`
3. Rebuild the project

## Alternative: Using Drawable Resources

If you prefer to use drawable resources instead of assets:

1. Copy icons to `app\src\main\res\drawable\` with naming: `ic_hotel.png`, `ic_restaurant.png`, etc.
2. The `FacilityMarkerLayer` will automatically detect and use them as fallback

## Notes

- Icon sizes match the specifications from `miyako_map.html`
- Parking icons use a different anchor point (10, 10) vs others (10, 20)
- Icons are cached in memory for performance




