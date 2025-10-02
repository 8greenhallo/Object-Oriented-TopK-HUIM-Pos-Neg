# OOTK-HUIM: Object-Oriented solution for Top-K High-Utility Itemset Mining from Uncertain Databases


```mermaid
---
title: Ver9 Algorithm - Complete Workflow Overview
---
graph TB
    Start([Start: mine method]) --> Init[Initialize Algorithm<br/>- Set parameters k, minPro<br/>- Create TopKManager CAS-based<br/>- Setup ForkJoinPool numCPUs]
    
    Init --> Phase1[Phase 1: Optimized Initialization<br/>Single-pass RTWU + Suffix Sum]
    
    Phase1 --> Pass1[Pass 1: Calculate RTWU<br/>- Compute Redefined TWU for items<br/>- Build global RTWU ordering<br/>- Create itemToRank mapping]
    
    Pass1 --> Pass2[Pass 2: Build Utility Lists<br/>- Suffix sum preprocessing O T<br/>- Direct element creation<br/>- Pre-computed EU/EP/Remaining]
    
    Pass2 --> Sort[Sort Items by RTWU Rank<br/>Low to High utility]
    
    Sort --> ProcessSingle[Process Single Items<br/>- Check EU ≥ threshold<br/>- Check EP ≥ minPro<br/>- Update TopK if qualified]
    
    ProcessSingle --> Decision{Dataset Size<br/>≥ PARALLEL_THRESHOLD?}
    
    Decision -->|Yes: Large Dataset| Phase2P[Phase 2: Parallel Mining<br/>ForkJoinPool Processing]
    Decision -->|No: Small Dataset| Phase2S[Phase 2: Sequential Mining<br/>Single Thread Processing]
    
    Phase2P --> PrefixTask[PrefixMiningTask<br/>Divide & Conquer<br/>Granularity: 7 items]
    Phase2S --> SeqMining[Sequential Mining Loop<br/>Process each prefix]
    
    PrefixTask --> ProcessPrefix[Process Each Prefix<br/>- Dynamic RTWU pruning<br/>- Filter viable extensions<br/>- Recursive search]
    
    SeqMining --> ProcessPrefix
    
    ProcessPrefix --> SearchDecision{Extension Count<br/>≥ PARALLEL_THRESHOLD?}
    
    SearchDecision -->|Yes| ExtensionTask[ExtensionSearchTask<br/>Parallel Extension Processing]
    SearchDecision -->|No| SeqSearch[Sequential Extension Search]
    
    ExtensionTask --> ProcessExt[Process Extension<br/>- Join utility lists<br/>- Apply pruning strategies<br/>- Update TopK]
    SeqSearch --> ProcessExt
    
    ProcessExt --> Pruning{Pruning Checks}
    
    Pruning -->|RTWU Prune| PruneRTWU[Skip: RTWU < threshold]
    Pruning -->|EP Prune| PruneEP[Skip: EP < minPro]
    Pruning -->|EU Prune| PruneEU[Skip: EU+Remaining < threshold]
    Pruning -->|Pass All| UpdateTopK[Update TopK via CAS<br/>Lock-free mechanism]
    
    UpdateTopK --> Recurse{More Extensions?}
    
    Recurse -->|Yes| ProcessPrefix
    Recurse -->|No| Complete[Mining Complete]
    
    PruneRTWU --> Complete
    PruneEP --> Complete
    PruneEU --> Complete
    
    Complete --> Results[Return Top-K Results<br/>Sorted by Expected Utility]
    Results --> End([End])
    
    style Phase1 fill:#e1f5ff
    style Phase2P fill:#ffe1e1
    style Phase2S fill:#ffe1e1
    style UpdateTopK fill:#e1ffe1
    style Pruning fill:#fff4e1
    style Results fill:#f0e1ff
```

1. check maven version:
- Maven installation size:
```bash
du -sh /opt/homebrew/Cellar/maven/*
```

2. Create new version(start from version/ver{i|i=1-9}/ e.g. version/ver1/ replace DartifactId):
```bash
mvn archetype:generate \
    -DgroupId=huim \
    -DartifactId=ver{i}_{1-9} \
    -DarchetypeArtifactId=maven-archetype-quickstart \
    -DinteractiveMode=false
```

4. Object-Oriented-TopK-HUIM-Pos-Neg is the root: go to directory version/ver{i}/ver{i}_{0-9}

- To compile/build:
```bash
mvn clean package
```

- To run main to check if Maven setup is fine (replace mainClass with directory to main in target)
```bash
mvn exec:java -Dexec.mainClass="ver{i}_{1-9}"
```

- Run with data (replace mainClass with directory to main in target)
```bash
mvn exec:java -Dexec.mainClass="ver{i}_{1-9}" \
    -Dexec.args="../../../data/test_database.txt ../../../data/test_profits.txt 100 0.2"
```
