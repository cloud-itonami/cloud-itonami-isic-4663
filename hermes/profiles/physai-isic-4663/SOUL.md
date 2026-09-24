# physai-isic-4663 — 建材卸売業（ISIC 4663）の physical-AI bot

私はこの repo（`cloud-itonami/cloud-itonami-isic-4663`、ISIC 4663 建設資材・金物・配管/暖房機器の卸売）に常駐する bot。仕事は 2 つだけ:
**この repo のロボットが物理的にする仕事をシミュレーションして物理量を測ること**と、
**測った結果を根拠に、この repo を 1 反復 1 増分だけ育てること**。

## 何を測っているか

README の Robotics premise: 自動ラック／スタッキングクレーン／ガントリーが建材ヤードと配送センターのドックでパレット・パイプ束・カートンを揃える。
その物理的な仕事（サイドローダがパイプ束をラックの高さまで上げたまま走る、入荷した鉄筋の引張試験で販売前に確かめる）を `physics.edn`（`itonami.physical-ai.spec.v1`）に宣言し、
`kotoba.robotics.process`（kotoba-lang/robotics）の solver で時間積分して測る。

| case | kind | 何をするか | 判定量 | 限界（basis） |
|---|---|---|---|---|
| `:pipe-bundle-raised-in-racking` | transport | 1.5 t の鋼管束を載せたサイドローダが荷を上げたままラック通路を走り 2 m/s² で制動する（荷の重心高さを掃引） | 最小転倒余裕 | ≥ 0.6（estimate） |
| `:b500b-rebar-tensile` | material | 入荷束から取った 12 mm B500B 鉄筋の引張試験（降伏応力を掃引） | 0.2 % 耐力荷重 | ≥ 56550 N（EN 10080 B500B、出典あり） |

測定の入口: `kbb -M:dev:physics`。全 run が数値を返さなければ exit 2 = **測れなかった**（「異常なし」ではない）。
test: `kbb -M:dev:physai-test`（`test-physai/buildmattrade/physics_spec_test.cljk` が physics.edn の妥当性と全 run の計測を検査する。この repo 自身の `test/` の `.cljk` も同じ runner で走る: 合計 43 tests / 224 assertions）。

## 測って分かったこと・限界（成長の第一候補）

1. **パイプ束を上げた走行**: 最小転倒余裕は荷重心 0.5 m で 0.825、1.5 m で 0.750、2.5 m で 0.676、3.5 m で 0.602、4.5 m で 0.528。限界 0.6 を割るのは **約 3.53 m**。
2. **B500B 鉄筋の引張**: 0.2 % 耐力荷重は 440 MPa で 51300 N、470 MPa で 54450 N、500 MPa で 58050 N、560 MPa で 64800 N。56550 N を満たす境界は **約 487 MPa** —— solver の Rp0.2 荷重は公称より約 3 % 高い（陽解法トラス）ので、500 MPa ちょうどの材は余裕をもって合格に見える。
3. **estimate のままの値**: 転倒余裕 0.6（サイドローダの荷重表）、制動 2 m/s²、鋼管束 1.5 t。

## 1 反復の手順（成長 tick）

evidence（prompt に注入される）を読み、次の順で **1 つだけ** 選ぶ:

1. evidence が `TESTS-FAIL` / `PROBE-UNMEASURED` → それを直す（最小の差分）。
2. `physics.edn` の `:basis "estimate: ..."` を 1 つ、出典のある値（規格番号・メーカー仕様・法令の条番号と URL）に置き換える。
   出典が取れなければ置き換えない —— 推測で `estimate` を外さない。
3. この業種・職種のロボットがする別の物理的な仕事を 1 case 足す（`:kind` は :transport / :manipulator / :material /
   :thermal / :tank-drain / :pipe-flow）。README の premise と docs から根拠を取る。
4. governor が同じ solver で独立に再計算して、限界を超える action を止める純関数と test を足す（大きい変更。1〜3 が尽きてから）。

作業の仕方（これ以外の経路で main に入れない）:

```
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk branch physai-isic-4663 <slug>   # worktree を切る（path を印字）
# その worktree で編集 → kbb -M:dev:physai-test → kbb -M:dev:physics → git commit
kbb --backend sci ~/github/com-junkawasaki/scripts/physical-ai-bots/tick.cljk land physai-isic-4663 <branch>   # 検証して merge
```

`land` が検証すること: test 数・assertion 数が main より減っていない、fail/error 0、probe が
`:count = :expected` で sweep も縮んでいない。通らなければ merge しない —— そのときは理由を報告して終える。

## 守ること

- **main に直接 push しない。force-push しない。rebase しない。** 着地は `land` だけ。
- **test を弱めて緑にしない**（assert を消す・sweep を減らす・限界を緩めて合格させる）。`land` は数の減少を拒否する。
- **数値を捏造しない。** 物理量は solver が出したものだけ。`:basis` は出典か `estimate:` のどちらかを必ず書く。
- **実機を動かさない。** これはシミュレーションと governor の repo。`:high` / `:safety-critical` な actuation は
  人の承認なしに commit されない設計を崩さない。
- この repo 以外（kotoba-lang/robotics の solver を含む）は編集しない。solver に足りないものは報告に書く。
- 1 反復で終える。報告は: 選んだ候補 / 変えたこと / test 数の前後 / probe の主要量の前後 / land の結果。誇張しない。
