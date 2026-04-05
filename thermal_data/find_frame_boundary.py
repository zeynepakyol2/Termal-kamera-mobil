"""
Sliding Window Frame Boundary Finder
=====================================
İki frame birleştiği noktada Y16 satırları arasındaki varyans aniden artar.
Bu script o noktayı bulur ve doğru frame'i decode eder.

Kullanım:
  python find_frame_boundary.py
"""

import numpy as np
import matplotlib.pyplot as plt
from pathlib import Path

W, H = 384, 288
FRAME_BYTES = W * H * 2  # 221184

def row_variance(data: bytes, offset: int, row: int) -> float:
    """Verilen offset'ten başlayarak row numaralı satırın varyansını döndür."""
    start = offset + row * W * 2
    end   = start + W * 2
    if end > len(data):
        return 0.0
    arr = np.frombuffer(data[start:end], dtype=np.uint16).astype(float)
    return float(arr.std())

def row_jump(data: bytes, offset: int, row: int) -> float:
    """İki ardışık satır arasındaki ortalama mutlak fark."""
    s1 = offset + row * W * 2
    s2 = s1 + W * 2
    e2 = s2 + W * 2
    if e2 > len(data):
        return 0.0
    r1 = np.frombuffer(data[s1:s2], dtype=np.uint16).astype(float)
    r2 = np.frombuffer(data[s2:e2], dtype=np.uint16).astype(float)
    return float(np.abs(r1 - r2).mean())

def find_best_offset(data: bytes, search_range: int = 500) -> tuple[int, float]:
    """
    0..search_range arasında her byte offset'i için:
    - 288 satırlık bir frame al
    - Her ardışık satır çifti arasındaki ortalama farka bak
    - En düşük maksimum jump'a sahip offset = en temiz frame
    """
    best_offset = 0
    best_score  = float('inf')
    scores      = []

    for off in range(min(search_range, len(data) - FRAME_BYTES)):
        jumps = [row_jump(data, off, r) for r in range(H - 1)]
        # Maksimum jump — gerçek frame sınırı geçişini temsil eder
        score = max(jumps)
        scores.append(score)
        if score < best_score:
            best_score  = score
            best_offset = off

    return best_offset, best_score, scores

def decode_frame(data: bytes, offset: int) -> np.ndarray:
    chunk = np.frombuffer(data[offset:offset + FRAME_BYTES], dtype=np.uint16).reshape(H, W)
    return chunk

def to_thermal_rgb(arr: np.ndarray) -> np.ndarray:
    """Y16 → termal renk haritası (mavi→yeşil→sarı→kırmızı)"""
    # Gürültülü uç değerleri filtrele
    valid = arr[(arr > 1000) & (arr < 64000)]
    if len(valid) == 0:
        mn, mx = arr.min(), arr.max()
    else:
        mn = np.percentile(valid, 2)
        mx = np.percentile(valid, 98)

    t = np.clip((arr.astype(float) - mn) / max(mx - mn, 1), 0, 1)

    r = np.zeros_like(t)
    g = np.zeros_like(t)
    b = np.zeros_like(t)

    # Mavi → cyan
    m = t < 0.25
    f = t[m] / 0.25
    r[m] = 0; g[m] = f; b[m] = 1.0

    # Cyan → yeşil
    m = (t >= 0.25) & (t < 0.5)
    f = (t[m] - 0.25) / 0.25
    r[m] = 0; g[m] = 1.0; b[m] = 1 - f

    # Yeşil → sarı
    m = (t >= 0.5) & (t < 0.75)
    f = (t[m] - 0.5) / 0.25
    r[m] = f; g[m] = 1.0; b[m] = 0

    # Sarı → kırmızı
    m = t >= 0.75
    f = (t[m] - 0.75) / 0.25
    r[m] = 1.0; g[m] = 1 - f; b[m] = 0

    rgb = np.stack([r, g, b], axis=-1)
    return (rgb * 255).astype(np.uint8)

def main():
    files = sorted(Path(".").glob("thermal_frame_*.bin"))
    if not files:
        print("thermal_frame_*.bin bulunamadı!")
        return

    print(f"{len(files)} dosya bulundu")

    for filepath in files[:3]:
        data = filepath.read_bytes()
        print(f"\n{'='*60}")
        print(f"Dosya: {filepath.name}  ({len(data)} bytes)")

        # ── Sliding window ile en iyi offset bul ──────────────────────────
        print("Offset aranıyor (0-500 byte)...")
        best_off, best_score, scores = find_best_offset(data, search_range=500)
        print(f"En iyi offset: {best_off} byte  (max_jump={best_score:.1f})")
        print(f"Offset=0 skoru: {scores[0]:.1f}")
        print(f"İyileşme: {((scores[0] - best_score) / max(scores[0], 1) * 100):.1f}%")

        # ── Frame'leri decode et ──────────────────────────────────────────
        frame_naive = decode_frame(data, 0)
        frame_best  = decode_frame(data, best_off)

        # ── Satır jump grafiği ────────────────────────────────────────────
        jumps_naive = [row_jump(data, 0,        r) for r in range(H - 1)]
        jumps_best  = [row_jump(data, best_off, r) for r in range(H - 1)]

        fig, axes = plt.subplots(2, 3, figsize=(16, 9))
        fig.suptitle(f"{filepath.name} — Sliding Window Analizi", fontsize=12)

        # Naive görüntü
        axes[0, 0].imshow(to_thermal_rgb(frame_naive))
        axes[0, 0].set_title(f"Offset=0 (naif)")
        axes[0, 0].axis('off')

        # En iyi offset görüntüsü
        axes[0, 1].imshow(to_thermal_rgb(frame_best))
        axes[0, 1].set_title(f"Offset={best_off} (sliding window)")
        axes[0, 1].axis('off')

        # Fark haritası
        diff = frame_best.astype(int) - frame_naive.astype(int)
        axes[0, 2].imshow(np.abs(diff), cmap='hot')
        axes[0, 2].set_title("Fark haritası")
        axes[0, 2].axis('off')

        # Jump grafiği — naive
        axes[1, 0].plot(jumps_naive, color='#FF6B35', linewidth=0.8)
        axes[1, 0].set_title("Satır jump'ları (offset=0)")
        axes[1, 0].set_xlabel("Satır no"); axes[1, 0].set_ylabel("Ort. fark")
        axes[1, 0].axhline(y=np.mean(jumps_naive), color='gray', linestyle='--', alpha=0.5)

        # Jump grafiği — best
        axes[1, 1].plot(jumps_best, color='#00BFA5', linewidth=0.8)
        axes[1, 1].set_title(f"Satır jump'ları (offset={best_off})")
        axes[1, 1].set_xlabel("Satır no"); axes[1, 1].set_ylabel("Ort. fark")
        axes[1, 1].axhline(y=np.mean(jumps_best), color='gray', linestyle='--', alpha=0.5)

        # Tüm offset'lerin skoru
        axes[1, 2].plot(scores[:200], color='#40C4FF', linewidth=0.8)
        axes[1, 2].axvline(x=best_off, color='#FF6B35', linestyle='--', label=f'best={best_off}')
        axes[1, 2].set_title("Offset skoru (düşük = iyi)")
        axes[1, 2].set_xlabel("Offset (byte)"); axes[1, 2].set_ylabel("Max jump")
        axes[1, 2].legend()

        plt.tight_layout()
        out = f"{filepath.stem}_sliding_window.png"
        plt.savefig(out, dpi=150, bbox_inches='tight',
                    facecolor='#08121F', edgecolor='none')
        print(f"Kaydedildi: {out}")
        plt.show()

        # ── Sonuç özeti ───────────────────────────────────────────────────
        temp_naive = (frame_naive.astype(float) - 4607) / 27.03
        temp_best  = (frame_best.astype(float)  - 4607) / 27.03

        valid_n = temp_naive[(temp_naive > -20) & (temp_naive < 100)]
        valid_b = temp_best [(temp_best  > -20) & (temp_best  < 100)]

        print(f"\nNaif  → geçerli piksel: %{100*len(valid_n)/frame_naive.size:.1f}  "
              f"ort: {valid_n.mean():.1f}°C" if len(valid_n) > 0 else "\nNaif  → geçerli piksel yok")
        print(f"Best  → geçerli piksel: %{100*len(valid_b)/frame_best.size:.1f}  "
              f"ort: {valid_b.mean():.1f}°C" if len(valid_b) > 0 else "Best  → geçerli piksel yok")

    print("\n✓ Tamamlandı!")

if __name__ == "__main__":
    main()
