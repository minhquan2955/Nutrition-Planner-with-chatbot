"""Check deterministic recipe portions and the downloaded PDF; no API calls."""
import hashlib
import json
from decimal import Decimal
from pathlib import Path
from pypdf import PdfReader

root = Path(__file__).resolve().parents[1]
data = json.loads((root / "docs/fixtures/phase1-foods.json").read_text(encoding="utf-8"), parse_float=Decimal)
assert len(data["recipes"]) == 3
for recipe in data["recipes"]:
    ratio = Decimal(str(recipe["quantityG"])) / 100
    for nutrient in ("kcal", "proteinG", "carbohydrateG", "fatG"):
        assert Decimal(str(recipe[nutrient])) * ratio == Decimal(str(recipe["expectedPerServing"][nutrient]))
    assert Decimal(str(recipe["simulatedPricePer100gVnd"])) * ratio == recipe["expectedPerServing"]["simulatedCostVnd"]
path = root / "docs/fixtures/fda-food-allergies-2024.pdf"
assert hashlib.sha256(path.read_bytes()).hexdigest() == "6b73da78a2397b31683e7bad11fa8fea057a8973695d639237de2c428d14409a"
pdf = PdfReader(path)
assert len(pdf.pages) == 4
assert all(len(page.extract_text()) > 100 for page in pdf.pages)
assert "Sesame" in pdf.pages[0].extract_text()
assert "Contains" in pdf.pages[1].extract_text()
print("PASS: 3 recipe portions, prices, PDF checksum, 4 text pages, QA references")
