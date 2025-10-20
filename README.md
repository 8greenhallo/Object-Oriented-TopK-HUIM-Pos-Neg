# OOTK-HUIM-UN-PNU: Object-Oriented solutions to the problem of Top-K High-Utility Itemsets Mining from UNcertain databases with both Positive and Negative Utilities

1. Check maven version:
- Maven installation size:
```bash
du -sh /opt/homebrew/Cellar/maven/*
```

2. Create new version(start from version/ver{i|i=1-9}/ e.g. version/ replace DartifactId):
```bash
mvn archetype:generate \
    -DgroupId=ootkhuimunpnu \
    -DartifactId=ver{i}_{0-9} \
    -DarchetypeArtifactId=maven-archetype-quickstart \
    -DinteractiveMode=false
```

3. Object-Oriented-TopK-HUIM-Pos-Neg is the root: go to version/ver{i}_{0-9}

- To compile/build:
```bash
mvn clean package
```

- To run main to check if Maven setup is fine (replace mainClass with directory to main in target)
```bash
mvn exec:java -Dexec.mainClass="ver01"
```

- Run with data for a specific version:
```bash
mvn exec:java -Dexec.mainClass="ver01" \
    -Dexec.args="../../data/test_database.txt ../../data/test_profits.txt 100 0.2"
```
