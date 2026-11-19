#!/usr/bin/env bash
# Rebuilds the bundled fonts in core/designsystem/src/commonMain/composeResources/font/ (i18n-and-design-system.md B,
# budget ≤ 450 KB). Needs: python3 -m pip install fonttools.
# Sources (OFL, licences in composeResources/files/licenses/):
#   https://raw.githubusercontent.com/google/fonts/main/ofl/baloo2/Baloo2%5Bwght%5D.ttf
#   https://raw.githubusercontent.com/google/fonts/main/ofl/hind/Hind-Regular.ttf
#   https://raw.githubusercontent.com/google/fonts/main/ofl/hind/Hind-SemiBold.ttf
# Usage: tools/fonts/subset.sh <dir with the three source files>
set -euo pipefail
SRC=${1:?source dir}
OUT="$(cd "$(dirname "$0")/../.." && pwd)/core/designsystem/src/commonMain/composeResources/font"
TMP=$(mktemp -d)
# Basic Latin, NBSP/soft hyphen/middle dot/×, Devanagari (no Vedic/Sindhi extras), ZW(N)J, punctuation, isolates, ₹, dotted circle.
U="U+0020-007E,U+00A0,U+00AD,U+00B7,U+00D7,U+0900-0950,U+0955-0972,U+200B-200D,U+2010-2014,U+2018-201D,U+2022,U+2026,U+2066-2069,U+20B9,U+2212,U+25CC"
F="akhn,blwf,half,pres,abvs,blws,psts,haln,rphf,nukt,locl,mark,mkmk,tnum,ccmp,rkrf,vatu,cjct,pstf,abvm,blwm,init,calt"
COMMON=(--unicodes="$U" --name-IDs='0,1,2,3,4,5,6,13,14' --notdef-outline --no-hinting)
fonttools varLib.instancer "$SRC/Baloo2[wght].ttf" wght=600 --static -o "$TMP/Baloo2-SemiBold.ttf"
# Baloo 2 (headings only): no kern/dist pair positioning — its GPOS pair table alone is ~70 KB.
pyftsubset "$TMP/Baloo2-SemiBold.ttf" "${COMMON[@]}" --layout-features="$F" --output-file="$OUT/baloo2_semibold.ttf"
pyftsubset "$SRC/Hind-Regular.ttf" "${COMMON[@]}" --layout-features="$F,kern,dist" --output-file="$OUT/hind_regular.ttf"
pyftsubset "$SRC/Hind-SemiBold.ttf" "${COMMON[@]}" --layout-features="$F,kern,dist" --output-file="$OUT/hind_semibold.ttf"
du -cb "$OUT"/*.ttf
