# メモ
## 参考
   - ベースのアプリは以下を参考
     - https://terasolunaorg.github.io/guideline/5.7.0.RELEASE/ja/Tutorial/TutorialREST.html
     
## 環境準備
 - 以下を参照すること。
   - https://github.com/ikeyat/demo-spanner-jdbc#%E6%BA%96%E5%82%99

## ローカル起動での確認
### ローカルでH2で確認
#### H2への向き先設定
`application.properties`の`spring.profiles.active`を`h2`に設定しているので、JVM引数に何も指定しなければデフォルトでH2に接続する。

```
spring.profiles.active=h2
```
#### ローカル起動
Spring Boot Appとして起動。
#### API 打鍵 (API Spec)
##### GET /todos/
```
curl -D - http://localhost:8080/todos/
```

##### POST /todos
```
curl -D - -X POST -H "Content-Type: application/json" -d '{"title": "Study Spring"}' http://localhost:8080/todos
```

##### PUT /todos/{id}
```
curl -D - -X PUT http://localhost:8080/todos/{id}
```

##### DELETE /todos/{id}
```
curl -D - -X DELETE http://localhost:8080/todos/{id}
```

### ローカルでSpannerエミュレータで確認
#### H2への向き先設定
起動時のJVM引数で`spring.profiles.active`を`spanner`に上書きすることで、Spannerに接続する。

```
-Dspring.profiles.active=spanner
```

#### ローカル起動
Spring Boot Appとして起動。
前述の通り、Spannerに接続するようJVM起動引数でProfileを指定する。

#### API 打鍵 (API Spec)
同じなので略。

### ローカルのDockerコンテナで確認
#### dockerコンテナをビルド
```
mvn spring-boot:build-image -Dspring-boot.build-image.imageName=demo-spanner/demo-spanner-jdbc-web
```

#### dockerコンテナを起動
H2接続で起動する場合は環境変数なし。
```
docker run -p 8080:8080 -it demo-spanner/demo-spanner-jdbc-web
```

Spanner接続で起動する場合は、Profile切り替えのため、コンテナの環境変数を変更する必要がある。
```
docker run -e SPRING_PROFILES_ACTIVE="spanner" -p 8080:8080 -it demo-spanner/demo-spanner-jdbc-web 
```

が、jdbcのURLがlocalhostのままではSpannerエミュレータには接続できないため、エラーとなるはず。
以下のように接続先を強制的に変更すれば、Dockerコンテナ起動でもローカルのSpannerに接続できるようになる。
```
docker run -e SPRING_PROFILES_ACTIVE="spanner" -e DEMO_SPANNER_HOST="//host.docker.internal:9010" -p 8080:8080 -it demo-spanner/demo-spanner-jdbc-web 
```

#### API 打鍵 (API Spec)
同じなので略。

## GKEでの確認