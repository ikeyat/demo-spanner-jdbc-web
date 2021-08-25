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
GitHubからCloud Buildを経由してGKEにデプロイするパイプラインを構築して、GKEへデプロイする。まずはH2接続を試す。

### GCPサービスの有効化
Cloud BuildおよびGKEをGCPのConsoleで有効化しておく。

### GKEクラスタの作成
検証用なので、単一ゾーンクラスタを作成、
バージョンはリリースチャネルに載せて自動Updateさせる。

https://cloud.google.com/kubernetes-engine/docs/how-to/creating-a-cluster?hl=ja#using-gcloud-config

```
$ gcloud config configurations create gcp-trial-ikeyat
$ gcloud config set project turnkey-rookery-323304
$
$ #初回時はログインが必要。出力されるURL似ブラウザでアクセスし、トークンをCUIにコピペ。
$ gcloud auth login
<URLが表示される>
Enter verification code:  <ブラウザからコピペ>

$ gcloud config set compute/zone asia-northeast1-a
...
WARNING: Currently VPC-native is not the default mode during cluster creation. In the future, this will become the default mode and can be disabled using `--no-enable-ip-alias` flag. Use `--[no-]enable-ip-alias` flag to suppress this warning.
WARNING: Starting with version 1.18, clusters will have shielded GKE nodes by default.
WARNING: Your Pod address range (`--cluster-ipv4-cidr`) can accommodate at most 1008 node(s). 
WARNING: Starting with version 1.19, newly created clusters and node-pools will have COS_CONTAINERD as the default node image when no image type is specified.
Creating cluster gke-trial in asia-northeast1-a...done.                        
Created [https://container.googleapis.com/v1/projects/turnkey-rookery-323304/zones/asia-northeast1-a/clusters/gke-trial].
To inspect the contents of your cluster, go to: https://console.cloud.google.com/kubernetes/workload_/gcloud/asia-northeast1-a/gke-trial?project=turnkey-rookery-323304
kubeconfig entry generated for gke-trial.
NAME       LOCATION           MASTER_VERSION  MASTER_IP       MACHINE_TYPE  NODE_VERSION    NUM_NODES  STATUS
gke-trial  asia-northeast1-a  1.20.8-gke.900  35.187.222.160  e2-medium     1.20.8-gke.900  3          RUNNING
```

Warningが出ているが、一旦進む。

### Cloud Buildパイプラインの作成
https://cloud.google.com/build/docs/deploying-builds/deploy-gke?hl=ja
に基本従って進めていく。

#### GKE Developerを有効にする。
https://cloud.google.com/build/docs/deploying-builds/deploy-gke?hl=ja#required_iam_permissions

#### マニフェストファイルをGitHubリポジトリ内に用意しておく
deployment/deployment.yml

#### ビルド構成ファイルをGitHubリポジトリ内に用意しておく
ci/cloudbuild.yml


