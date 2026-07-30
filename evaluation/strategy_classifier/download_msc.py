"""Download MSC pre-extracted global features from Google Drive.

The MSC dataset (Wu et al. 2017) provides per-timestep sparse matrices
for ~36k SC2 replays. Each replay has two players' features plus metadata.

Usage: python3 -m evaluation.strategy_classifier.download_msc
"""
import os
import urllib.request
import zipfile
from pathlib import Path
from evaluation.strategy_classifier.config import Paths

MSC_URLS = {
    "GlobalFeatures": "https://drive.google.com/uc?export=download&id=1y6oJSVjYdMFfHmNbRVh0vMzAxDeSQ-pE",
}


def download_msc(paths: Paths = Paths()) -> Path:
    dest = paths.data / "msc"
    dest.mkdir(parents=True, exist_ok=True)

    marker = dest / ".download_complete"
    if marker.exists():
        print(f"MSC data already downloaded at {dest}")
        return dest

    for name, url in MSC_URLS.items():
        zip_path = dest / f"{name}.zip"
        print(f"Downloading {name}...")
        urllib.request.urlretrieve(url, zip_path)
        print(f"Extracting {name}...")
        with zipfile.ZipFile(zip_path, "r") as zf:
            zf.extractall(dest)
        zip_path.unlink()

    marker.touch()
    print(f"MSC data ready at {dest}")
    return dest


if __name__ == "__main__":
    download_msc()
