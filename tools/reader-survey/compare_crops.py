#!/usr/bin/env python3
"""
tools/reader-survey/compare_crops.py
기준 크롭 결과(base-crop.txt)와 새 크롭 결과(new-crop.txt)를 비교하여
0.5% 넘게 더 잘라낸 쪽(more crop, 그림 손상 위험)과 덜 잘라낸 쪽(less crop)을 분석한다.
"""

import sys
import os
from PIL import Image

def load_crop_file(filepath):
    results = {}
    if not os.path.exists(filepath):
        print(f"Error: {filepath} not found.")
        sys.exit(1)
    with open(filepath, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line.startswith('CROP '):
                continue
            parts = line.split(' ', 3)
            if len(parts) < 4:
                continue
            n = int(parts[1])
            crop = [int(v) for v in parts[2].split(',')]
            path = parts[3].strip()
            results[n] = {'crop': crop, 'path': path}
    return results

def main():
    if len(sys.argv) < 3:
        base_file = 'D:/Project/KomgaTV-gutters-data/round3-crop/base-crop.txt'
        new_file = 'D:/Project/KomgaTV-gutters-data/round3-crop/new-crop.txt'
    else:
        base_file = sys.argv[1]
        new_file = sys.argv[2]

    print(f"Comparing:")
    print(f"  BASE: {base_file}")
    print(f"  NEW : {new_file}")

    base = load_crop_file(base_file)
    new = load_crop_file(new_file)

    common_keys = sorted(set(base.keys()) & set(new.keys()))
    print(f"Common pages: {len(common_keys)}")

    more_cut = []
    less_cut = []
    same = 0

    dim_cache = {}

    for idx, n in enumerate(common_keys):
        b_crop = base[n]['crop']
        n_crop = new[n]['crop']
        path = base[n]['path']

        if b_crop == n_crop:
            same += 1
            continue

        # 이미지 크기 로드
        if path not in dim_cache:
            try:
                with Image.open(path) as img:
                    dim_cache[path] = img.size # (w, h)
            except Exception:
                dim_cache[path] = (1000, 1500)
        w, h = dim_cache[path]

        dt = n_crop[0] - b_crop[0]
        db = n_crop[1] - b_crop[1]
        dl = n_crop[2] - b_crop[2]
        dr = n_crop[3] - b_crop[3]

        h_thresh = max(2, int(h * 0.005))
        w_thresh = max(2, int(w * 0.005))

        is_more = (dt > h_thresh) or (db > h_thresh) or (dl > w_thresh) or (dr > w_thresh)
        is_less = (dt < -h_thresh) or (db < -h_thresh) or (dl < -w_thresh) or (dr < -w_thresh)

        if is_more:
            more_cut.append((n, b_crop, n_crop, (dt, db, dl, dr), (w, h), path))
        elif is_less:
            less_cut.append((n, b_crop, n_crop, (dt, db, dl, dr), (w, h), path))
        else:
            same += 1

    print("\n=== 비교 결과 요약 ===")
    print(f"전체 공통 쪽 수: {len(common_keys)}")
    print(f"변화 없음 (0.5% 이내 미세 차이 포함): {same} 쪽")
    print(f"더 잘라낸 쪽 (More cut > 0.5%): {len(more_cut)} 쪽")
    print(f"덜 잘라낸 쪽 (Less cut > 0.5%): {len(less_cut)} 쪽")

    if more_cut:
        print("\n[주의] 더 잘라낸 쪽 목록 (그림 손상 위험 점검 필수):")
        for item in more_cut[:50]:
            n, b_crop, n_crop, diff, dim, p = item
            print(f"  n={n:04d} base={b_crop} new={n_crop} diff={diff} size={dim} {p}")
        if len(more_cut) > 50:
            print(f"  ... 외 {len(more_cut) - 50} 쪽")

    if less_cut:
        print("\n덜 잘라낸 쪽 샘플 (안전 보존 또는 여백 잔여):")
        for item in less_cut[:30]:
            n, b_crop, n_crop, diff, dim, p = item
            print(f"  n={n:04d} base={b_crop} new={n_crop} diff={diff} size={dim} {p}")
        if len(less_cut) > 30:
            print(f"  ... 외 {len(less_cut) - 30} 쪽")

    # 결과 리포트 파일 저장
    out_report = 'D:/Project/KomgaTV-gutters-data/round3-crop/crop_comparison_report.txt'
    with open(out_report, 'w', encoding='utf-8') as f:
        f.write(f"=== CROP COMPARISON REPORT ===\n")
        f.write(f"BASE: {base_file}\nNEW : {new_file}\n")
        f.write(f"Total: {len(common_keys)}, Same: {same}, More: {len(more_cut)}, Less: {len(less_cut)}\n\n")
        f.write(f"--- MORE CUT ({len(more_cut)}) ---\n")
        for item in more_cut:
            f.write(f"n={item[0]:04d} base={item[1]} new={item[2]} diff={item[3]} size={item[4]} {item[5]}\n")
        f.write(f"\n--- LESS CUT ({len(less_cut)}) ---\n")
        for item in less_cut:
            f.write(f"n={item[0]:04d} base={item[1]} new={item[2]} diff={item[3]} size={item[4]} {item[5]}\n")
    print(f"\n상세 리포트 저장 완료: {out_report}")

if __name__ == '__main__':
    main()
