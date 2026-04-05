"""
HIKMICRO Mini3 Ham Frame Analiz - v2
Çözünürlük: 384x288, Y16 format
Sıcaklık formülü: T = (value - 4607.03) / 27.03  (Mini2Plus'tan türetilmiş)
"""

import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path
import sys

W, H = 384, 288
FRAME_BYTES = W * H * 2  # 221184

# Mini2Plus'tan türetilmiş sabitler — Mini3 için yaklaşık
OFFSET = 4607.03
GAIN   = 27.03

def raw_to_celsius(arr):
    return (arr.astype(float) - OFFSET) / GAIN

def find_best_offset(data: bytes) -> int:
    """
    Fazla byte'ların nerede başladığını bul.
    Y16 verisinde bitişik piksel değerleri birbirine yakın olmalı.
    En az varyasyona sahip offset'i seç.
    """
    best_offset = 0
    best_score  = float('inf')

    # 0'dan 500'e kadar her offset'i dene
    for off in range(0, min(500, len(data) - FRAME_BYTES)):
        chunk = np.frombuffer(data[off:off + FRAME_BYTES], dtype=np.uint16)
        # Geçersiz piksel sayısı (çok düşük veya çok yüksek)
        invalid = np.sum((chunk < 100) | (chunk > 60000))
        if invalid < best_score:
            best_score  = invalid
            best_offset = off

    return best_offset, best_score

def analyze(filepath: Path):
    data = filepath.read_bytes()
    size = len(data)
    extra = size - FRAME_BYTES

    print(f"\n{'='*60}")
    print(f"Dosya : {filepath.name}")
    print(f"Boyut : {size} bytes  (beklenen {FRAME_BYTES}, fazla={extra:+d})")
    print(f"İlk 8 : {list(data[:16])}")

    # ── Offset bul ────────────────────────────────────────────────────────
    offset, invalid_px = find_best_offset(data)
    print(f"\nEn iyi offset : {offset} byte  (geçersiz piksel: {invalid_px})")

    chunk = np.frombuffer(data[offset:offset + FRAME_BYTES], dtype=np.uint16).reshape(H, W)

    print(f"\nHam Y16 istatistikleri:")
    print(f"  Min   : {chunk.min():5d}  (0x{chunk.min():04X})")
    print(f"  Max   : {chunk.max():5d}  (0x{chunk.max():04X})")
    print(f"  Ort   : {chunk.mean():8.1f}")
    print(f"  Std   : {chunk.std():8.1f}")

    temp = raw_to_celsius(chunk)
    # Geçerli sıcaklık aralığı: -20°C ile 150°C arası
    valid = temp[(temp >= -20) & (temp <= 150)]

    print(f"\nSıcaklık tahmini (T = (D-{OFFSET:.0f}) / {GAIN:.2f}):")
    print(f"  Tüm piksel   Min: {temp.min():7.1f}°C  Max: {temp.max():7.1f}°C")
    if len(valid) > 0:
        print(f"  Geçerli bölge Min: {valid.min():7.1f}°C  Max: {valid.max():7.1f}°C  Ort: {valid.mean():7.1f}°C")
        print(f"  Geçerli piksel oranı: %{100*len(valid)/chunk.size:.1f}")

    # ── Görselleştir ──────────────────────────────────────────────────────
    fig, axes = plt.subplots(1, 3, figsize=(16, 5))
    fig.suptitle(f"{filepath.name}  —  offset={offset}  fazla={extra:+d}B", fontsize=11)

    # 1. Ham normalize
    ax = axes[0]
    mn, mx = np.percentile(chunk, 2), np.percentile(chunk, 98)
    im = ax.imshow(chunk, cmap='gray', vmin=mn, vmax=mx)
    ax.set_title(f"Ham Y16 (p2={mn:.0f} p98={mx:.0f})")
    plt.colorbar(im, ax=ax)

    # 2. Termal renk (inferno)
    ax = axes[1]
    im = ax.imshow(chunk, cmap='inferno', vmin=mn, vmax=mx)
    ax.set_title("Termal renk (Inferno)")
    plt.colorbar(im, ax=ax)

    # 3. Sıcaklık haritası (sadece geçerli aralık)
    ax = axes[2]
    t_clip = np.clip(temp, -20, 150)
    im = ax.imshow(t_clip, cmap='RdYlBu_r', vmin=-20, vmax=80)
    ax.set_title(f"Sıcaklık °C (clip -20..150)")
    plt.colorbar(im, ax=ax)

    plt.tight_layout()
    out = f"{filepath.stem}_analysis.png"
    plt.savefig(out, dpi=150, bbox_inches='tight')
    print(f"\nGörsel kaydedildi: {out}")
    plt.show()

    # ── Histogram ─────────────────────────────────────────────────────────
    fig, axes = plt.subplots(1, 2, figsize=(12, 4))
    fig.suptitle(f"Histogram — {filepath.name}", fontsize=11)

    axes[0].hist(chunk.flatten(), bins=200, color='steelblue', edgecolor='none')
    axes[0].set_title("Ham Y16 değerleri")
    axes[0].set_xlabel("Dijital sayım")
    axes[0].set_ylabel("Piksel sayısı")
    axes[0].axvline(x=OFFSET, color='red', linestyle='--', label=f'offset={OFFSET:.0f}')
    axes[0].legend()

    axes[1].hist(valid if len(valid) > 0 else temp.flatten(),
                 bins=100, color='coral', edgecolor='none')
    axes[1].set_title("Sıcaklık dağılımı (°C)")
    axes[1].set_xlabel("°C")

    plt.tight_layout()
    plt.savefig(f"{filepath.stem}_histogram.png", dpi=150)
    plt.show()

    # ── Offset olmadan (naif) karşılaştırma ──────────────────────────────
    if offset != 0:
        naive = np.frombuffer(data[:FRAME_BYTES], dtype=np.uint16).reshape(H, W)
        fig, axes = plt.subplots(1, 2, figsize=(12, 5))
        fig.suptitle("Offset karşılaştırması", fontsize=11)
        axes[0].imshow(naive, cmap='inferno',
                       vmin=np.percentile(naive, 2), vmax=np.percentile(naive, 98))
        axes[0].set_title(f"Offset=0 (naif)")
        axes[1].imshow(chunk, cmap='inferno', vmin=mn, vmax=mx)
        axes[1].set_title(f"Offset={offset} (düzeltilmiş)")
        plt.tight_layout()
        plt.savefig(f"{filepath.stem}_offset_compare.png", dpi=150)
        plt.show()


def main():
    files = sorted(Path(".").glob("thermal_frame_*.bin"))
    if not files:
        print("thermal_frame_*.bin bulunamadı!")
        sys.exit(1)

    print(f"{len(files)} frame dosyası bulundu:")
    for f in files:
        print(f"  {f.name}  ({f.stat().st_size} bytes)")

    # İlk 2'yi analiz et
    for f in files[:2]:
        analyze(f)

    print("\n✓ Tamamlandı! PNG dosyalarını incele.")


if __name__ == "__main__":
    main()