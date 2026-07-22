(ns buildmattrade.facts
  "Per-jurisdiction downstream building-materials-wholesale regulatory
  catalog -- the G2-style spec-basis table the Potable Water Safety
  Governor checks every `:certification/verify` proposal against ('did
  the advisor cite an OFFICIAL public source for this jurisdiction's
  building-materials-trade / sanctions requirements, or did it invent
  one?').

  Like every principal-trading sibling in this fleet, this catalog stays
  deliberately GENERIC -- the same shape as the fuel-wholesale /
  ag-machinery-wholesale / household-goods-wholesale siblings' own
  catalogs (credit-clearance record, contract/PO, sanctions-screening
  record) -- and does NOT fold the lead-free-certification fact into a
  per-jurisdiction checklist item: that is `buildmattrade.governor`'s own
  dedicated HARD check (`lead-free-certification-missing-violations`),
  not a jurisdiction-catalog checklist entry. See `buildmattrade.governor`
  namespace docstring for the full reasoning on why this stays a
  SEPARATE, type-gated check rather than a checklist item.

  Each entry below is a REAL jurisdiction with a REAL downstream
  building-materials-wholesale / potable-water-product regime:

  - USA (the PRIMARY regime for this vertical's own defining
    regulatory content): the Reduction of Lead in Drinking Water Act of
    2011 (Pub. L. 111-380, signed January 4, 2011) amended the Safe
    Drinking Water Act §1417 (42 U.S.C. §300g-6) to redefine 'lead free'
    for any pipe, pipe fitting, plumbing fitting or fixture intended to
    convey or dispense water for human consumption as not more than
    0.25% lead by WEIGHTED AVERAGE of the wetted surfaces -- a real,
    significant tightening from the PRIOR 8% lead-content ceiling the
    original 1986 SDWA lead-ban amendments had set. The 0.25%
    weighted-average definition became effective January 4, 2014 (the
    statute's own 3-year transition window). EPA administers SDWA §1417;
    compliance is demonstrated via independent third-party certification
    to NSF/ANSI/CAN 372 (Drinking Water System Components -- Lead
    Content, the weighted-average lead-content test methodology) and/or
    NSF/ANSI/CAN 61 (Drinking Water System Components -- Health Effects,
    the broader leaching/health-effects standard covering ALL regulated
    contaminants, not lead alone), both administered by ANSI-accredited
    third-party certification bodies -- NSF International being the
    best-known, though CSA Group, IAPMO R&T and UL Solutions also
    operate ANSI-accredited certification programs against these same
    standards. This build cites BOTH standards because a compliant
    'lead-free' potable-water product in practice needs both arms: the
    NSF/372 weighted-average lead-content test AND an NSF/61-scope
    health-effects certification for the fixture as a whole -- see
    `buildmattrade.governor` for how this build folds both into ONE
    check. OFAC sanctions programs apply as in every sibling's own
    counterparty-diligence checklist.
  - JPN (the second seeded jurisdiction, flagged with EXPLICIT lower
    confidence -- see 'Jurisdiction coverage (honest)' in
    `docs/business-model.md`): 水道法 (Waterworks Act, Act No. 177 of
    1957) Article 16 requires water-supply fittings (給水装置) to meet
    構造材質基準 (construction-and-material standards) set out in a
    implementing ministerial ordinance (給水装置の构造及び材质の基准に关する
    省令, 平成9年厚生省令第14号 / 1997), which includes a leaching
    (浸出) performance standard limiting lead and other substances from
    materials in contact with potable water -- the Japanese structural
    analog of the US weighted-average lead-free rule, though this
    build's confidence in the exact numeric leaching limit and in which
    ministry CURRENTLY holds this jurisdiction is only MODERATE: 水道法
    was historically administered by 厚生労働省 (MHLW), but this build's
    understanding is that Japan's 2024 administrative reorganization
    transferred water-supply infrastructure jurisdiction toward 国土交通省
    (MLIT) and water-quality standard-setting toward 环境省 (the Ministry
    of Environment) -- this build has NOT independently re-verified the
    exact post-reorganization split and cites 厚生労働省 as the
    historically-responsible authority pending that verification. JWWA
    (日本水道协会, the Japan Water Works Association) operates a
    widely-used private certification mark (JWWA规格适合品) against the
    ministerial leaching standard, structurally analogous to NSF's role
    in the US regime, though this build has NOT independently confirmed
    JWWA certification is the EXCLUSIVE or MANDATORY conformity route
    (as opposed to one of several accepted third-party routes) under
    current 水道法 practice. OFAC等同等制裁プログラム (OFAC-equivalent
    sanctions programs) apply as in every sibling's own
    counterparty-diligence checklist.
  - BRA (the third seeded jurisdiction, added 2026-07-23 -- and, UNLIKE
    USA/JPN above, this build could NOT find a Brazilian analog of the
    domain-defining 'lead-free potable-water-contact material' mandate;
    see 'BRA coverage gap (honest)' below): INMETRO (Instituto Nacional
    de Metrologia, Qualidade e Tecnologia) operates the Sistema
    Brasileiro de Avaliação da Conformidade (SBAC) / Programa de
    Avaliação da Conformidade (PAC), a compulsory (mandatory)
    third-party product-certification regime -- this build directly
    verified, via INMETRO's own official 'Regulamentos Técnicos e
    Programas de Avaliação da Conformidade Compulsórios' list, TWO
    building-materials-wholesale-relevant compulsory items: item 12,
    'Barras e Fios de Aço destinados a Armaduras para Estruturas de
    Concreto Armado' (reinforcing steel bars/wire for concrete
    structures), Portaria Inmetro n.° 139 de 19/03/2021; and item 41,
    'Conexões de Ferro Fundido Maleável para a Condução de Fluidos'
    (malleable cast-iron pipe fittings for fluid conduction), Portaria
    Inmetro n.° 390 de 21/09/2021 -- both mechanism 'Certificação',
    órgão regulamentador 'Inmetro', tipo 'PAC'. Additionally, Código de
    Defesa do Consumidor (Consumer Protection Code, Lei n.° 8.078 de 11
    de setembro de 1990) Art. 31 requires 'informações corretas, claras,
    precisas, ostensivas e em língua portuguesa' (correct, clear,
    precise, conspicuous, Portuguese-language information) -- including
    composition, quantity, origin, and health/safety risks -- on the
    offer/presentation of ANY product sold in Brazil, verified directly
    at the official planalto.gov.br compiled-law text. OFAC-equivalent
    sanctions programs apply as in every sibling's own
    counterparty-diligence checklist.

    **BRA coverage gap (honest):** this build's attempt to find a
    Brazilian standard specifically limiting LEAD CONTENT in
    potable-water-contact pipes/pipe fittings/plumbing fixtures --
    structurally analogous to the US Reduction of Lead in Drinking Water
    Act (SDWA §1417) or Japan's 水道法 leaching(浸出) standard, i.e. THIS
    vertical's own domain-defining regulatory content -- did NOT
    succeed, and this gap is disclosed rather than papered over with a
    guess: (1) this build directly read INMETRO's own current compulsory-
    certification list end-to-end this session and confirmed it contains
    NO PVC-pipe / potable-water-pipe / plumbing-fixture entry of any
    kind (only the two construction-material items cited above); (2)
    this build's attempt to independently fetch ANVISA/Ministério da
    Saúde's water-potability consolidation ordinance (Portaria de
    Consolidação n.° 5/2017, GM/MS, Anexo XX) to check whether it reaches
    materials (as opposed to only water-quality parameters) failed this
    session (connection reset by the host, and no Internet Archive
    Wayback Machine snapshot of that exact URL was available) -- so this
    build neither confirms nor denies that angle, it discloses the
    source as unreachable. Consequently this catalog entry's
    `:required-evidence` is, honestly, the SAME generic
    counterparty-diligence set as every other jurisdiction and does NOT
    imply BRA has (or lacks) a lead-free-certification-equivalent regime
    -- `buildmattrade.governor`'s own `lead-free-certification-missing`
    check remains scoped to the facts actually on a `building-order`
    record (`:potable-water-contact?` etc.), independent of jurisdiction,
    so this gap does not weaken that check; it only means this build
    cannot cite a BRA-specific STATUTORY analog for it the way it can
    for USA/JPN.

  The required-evidence set (credit-clearance record, contract/PO,
  sanctions-screening (OFAC/equivalent) record) mirrors the GENERIC
  counterparty-diligence evidence every principal-trading sibling's own
  catalog demands before ANY order proceeds -- it deliberately does NOT
  include a lead-free-certification item: unlike a jurisdiction-scoped
  checklist entry, THIS vertical's own domain-defining concern is
  evaluated by its own SEPARATE, dedicated, TYPE-GATED governor check
  (see `buildmattrade.governor`), not a checklist item.

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses: a jurisdiction not in
  this table has NO spec-basis, full stop -- the advisor must not
  fabricate one, and the governor holds if it tries.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` is the GENERIC
  counterparty-diligence evidence set (credit-clearance record,
  contract/PO, sanctions-screening record); `:legal-basis` /
  `:owner-authority` / `:provenance` are the G2 citation the governor
  requires before any `:certification/verify` proposal can commit.
  Deliberately does NOT include a lead-free-certification checklist item
  -- that is `buildmattrade.governor`'s own dedicated, type-gated HARD
  check."
  {"USA" {:name "USA"
          :owner-authority "U.S. Environmental Protection Agency (EPA) -- Safe Drinking Water Act §1417"
          :legal-basis "Reduction of Lead in Drinking Water Act of 2011 (Pub. L. 111-380), amending Safe Drinking Water Act §1417 (42 U.S.C. §300g-6): 'lead free' means not more than 0.25% lead by weighted average of wetted surfaces for any pipe, pipe fitting, plumbing fitting or fixture intended to convey or dispense water for human consumption, effective January 4, 2014 (superseding the prior 8% lead-content ceiling); NSF/ANSI/CAN 372 (Drinking Water System Components -- Lead Content) and NSF/ANSI/CAN 61 (Drinking Water System Components -- Health Effects), third-party certification administered by ANSI-accredited certification bodies (e.g. NSF International); OFAC sanctions programs"
          :provenance "https://www.epa.gov/dwreginfo/use-lead-free-pipes-fittings-fixtures-solder-and-flux-drinking-water"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}
   "JPN" {:name "JPN"
          :owner-authority "厚生労働省 (MHLW, historically) -- 水道法 Article 16 給水装置の构造材质基准"
          :legal-basis "水道法 (Waterworks Act, Act No. 177 of 1957) Article 16; 给水装置の构造及び材质の基准に关する省令 (平成9年厚生省令第14号, 1997) -- 浸出(leaching)性能基准 limiting lead and other substances from potable-water-contact materials; JWWA规格适合品 third-party certification mark (日本水道协会); OFAC等同等制裁プログラム"
          :provenance "https://www.mhlw.go.jp/"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}
   "BRA" {:name "BRA"
          :owner-authority "INMETRO (Instituto Nacional de Metrologia, Qualidade e Tecnologia) -- Sistema Brasileiro de Avaliação da Conformidade (SBAC), Programa de Avaliação da Conformidade (PAC) compulsory product certification"
          :legal-basis "Sistema Brasileiro de Avaliação da Conformidade (SBAC) / Programa de Avaliação da Conformidade (PAC), administered by INMETRO: compulsory third-party certification confirmed for 'Barras e Fios de Aço destinados a Armaduras para Estruturas de Concreto Armado' (reinforcing steel bars/wire for concrete structures, Portaria Inmetro n.° 139 de 19/03/2021) and 'Conexões de Ferro Fundido Maleável para a Condução de Fluidos' (malleable cast-iron pipe fittings for fluid conduction, Portaria Inmetro n.° 390 de 21/09/2021); Código de Defesa do Consumidor (Lei n.° 8.078/1990) Art. 31 requires correct, clear, precise, conspicuous, Portuguese-language information (composition, quantity, origin, health/safety risks) on any product offered for sale; OFAC-equivalent sanctions programs apply as in every sibling's own counterparty-diligence checklist. Does NOT include a lead-content/lead-free potable-water-contact-materials mandate -- this build could not verify one exists; see namespace docstring 'BRA coverage gap (honest)' for the full disclosure of what was and was not independently confirmed"
          :provenance "https://web.archive.org/web/20250526132136/http://inmetro.gov.br/qualidade/rtepac/compulsorios.asp"
          :required-evidence ["credit-clearance record"
                              "contract/PO"
                              "sanctions-screening (OFAC/equivalent) record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO spec-basis,
  and the governor must hold any proposal that tries to dispatch
  building materials or settle an invoice on it."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing jurisdiction
  as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-4663 R0: " (count catalog)
                 " jurisdictions seeded with an official spec-basis. "
                 "This is a starting catalog, not a survey of all ~194 "
                 "jurisdictions -- extend `buildmattrade.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  "Does `submitted` (a set/coll of evidence keywords or strings) satisfy
  every evidence item listed for `iso3`? Missing spec-basis -> never
  satisfied."
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
