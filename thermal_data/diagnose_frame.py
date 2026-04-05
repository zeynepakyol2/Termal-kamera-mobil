"""
HIKMICRO Mini3 - Metadata satırı analizi
Frame boyutu 221184'ten fazla — son satırlar metadata mı?
"""
import numpy as np
from pathlib import Path

W, H = 384, 288
FRAME_BYTES = W * H * 2

files = sorted(Path(".").glob("thermal_frame_*.bin"))
if not files:
    print("Dosya bulunamadı!"); exit()

data = files[0].read_bytes()
size = len(data)
extra_bytes = size - FRAME_BYTES
extra_pixels = extra_bytes // 2

print(f"Dosya: {files[0].name}")
print(f"Boyut: {size} bytes")
print(f"Fazla: {extra_bytes} bytes = {extra_pixels} piksel")
print(f"Fazla satır (384 piksel/satır): {extra_pixels / W:.2f} satır")
print()

# Ham diziyi yükle
arr = np.frombuffer(data, dtype=np.uint16)
total_pixels = len(arr)
print(f"Toplam piksel: {total_pixels}")
print(f"= {W}x{H} + {total_pixels - W*H} piksel")
print()

# Her satırın min/max/ort değerini göster (son 10 satır önemli)
print("Son 15 satırın istatistikleri (metadata satırı var mı?):")
print(f"{'Satır':>6}  {'Min':>6}  {'Max':>6}  {'Ort':>8}  {'Std':>8}  Not")
print("-" * 55)

rows_total = total_pixels // W
for row_idx in range(max(0, rows_total - 15), rows_total):
    start = row_idx * W
    end = start + W
    if end > total_pixels:
        row = arr[start:total_pixels]
    else:
        row = arr[start:end]
    
    note = ""
    if row.min() == 0 and row.max() < 1000:
        note = "← sıfır satır?"
    elif row.std() < 10:
        note = "← sabit değer? (metadata?)"
    elif row.min() > 60000 or row.max() > 63000:
        note = "← yüksek değer? (metadata?)"
    
    if row_idx >= H:
        note += " *** EKSTRA SATIR ***"
    
    print(f"{row_idx:>6}  {row.min():>6}  {row.max():>6}  {row.mean():>8.1f}  {row.std():>8.1f}  {note}")

print()
print("Normal görüntü satır aralığı: 0-287")
print(f"Bu dosyadaki toplam satır: {rows_total} (fazla: {rows_total - H})")

# İlk 288 satır (normal görüntü) istatistikleri
normal = np.frombuffer(data[:FRAME_BYTES], dtype=np.uint16).reshape(H, W)
print(f"\nNormal görüntü (0-287. satır):")
print(f"  Min: {normal.min()}  Max: {normal.max()}  Ort: {normal.mean():.1f}  Std: {normal.std():.1f}")

# Son ekstra satırlar
if extra_pixels >= W:
    extra = np.frombuffer(data[FRAME_BYTES:FRAME_BYTES + (extra_pixels//W)*W*2], dtype=np.uint16)
    print(f"\nEkstra satırlar ({extra_pixels} piksel):")
    print(f"  {extra[:min(32, len(extra))].tolist()}")
