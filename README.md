# メモ
## ゴール
 - GKEへのSpring Bootアプリケーションのデプロイ
   - GitHub -> Cloud Buildのパイプラインを使用
 - Spring BootアプリケーションからGCPのSpannerのアクセス
 - ApigeeXによるSpring Bootアプリケーション各APIのプロキシ化
 
## 参考
   - ベースのアプリは以下を参考に。
     - https://terasolunaorg.github.io/guideline/5.7.0.RELEASE/ja/Tutorial/TutorialREST.html
     - https://github.com/ikeyat/demo-spanner-jdbc
     
## 環境準備
 - ローカル環境については以下を参照すること。
   - https://github.com/ikeyat/demo-spanner-jdbc#%E6%BA%96%E5%82%99
 - GCPにプロジェクトを開設
   - 本ドキュメントではプロジェクトIDを`turnkey-rookery-323304`として記述。
   - 利用サービスは以下
     -    GKE（内部で以下が生成）
         - GCE
         - Cloud Load Balancing
     - Spanner
     - Cloud Build
     - Artifact Registry(Container Registry)
     - ApigeeX

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
$ curl -D - http://localhost:8080/todos/
```

##### POST /todos

```
$ curl -D - -X POST -H "Content-Type: application/json" -d '{"title": "Study Spring"}' http://localhost:8080/todos
```

##### PUT /todos/{id}

```
$ curl -D - -X PUT http://localhost:8080/todos/{id}
```

##### DELETE /todos/{id}

```
$ curl -D - -X DELETE http://localhost:8080/todos/{id}
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
$ mvn spring-boot:build-image -Dspring-boot.build-image.imageName=demo-spanner/demo-spanner-jdbc-web
```

#### dockerコンテナを起動
H2接続で起動する場合は環境変数なし。

```
$ docker run -p 8080:8080 -it demo-spanner/demo-spanner-jdbc-web
```

Spanner接続で起動する場合は、Profile切り替えのため、コンテナの環境変数を変更する必要がある。

```
$ docker run -e SPRING_PROFILES_ACTIVE="spanner" -p 8080:8080 -it demo-spanner/demo-spanner-jdbc-web 
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
#### GKEクラスタの初期作成
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


GKEが割り当てられているVPCのデフォルトは、全リージョンが含まれている。
使わないリージョンが含まれるのはネットワークリソースの無駄なので、必要なリージョン以外は削除するのが良いが、いったん後回しとする。

#### GKEクラスタのノード構成の修正（任意）
デフォルトだとノード数が3つであったりと検証用には無駄が多いので、ノード構成を以下に修正する。

 - ノード数：3 -> 1
 - インスタンスタイプ：e2-medium -> いったん変えない

##### ノードプールの確認
https://cloud.google.com/kubernetes-engine/docs/how-to/node-pools?hl=ja#viewing_node_pools_in_a_cluster

```
# ノードプールの名前を取得
$ gcloud container node-pools list --cluster gke-trial
NAME          MACHINE_TYPE  DISK_SIZE_GB  NODE_VERSION
default-pool  e2-medium     100           1.20.8-gke.900

# 取得したノードプール名を指定し、ノードプールの詳細を確認
$ gcloud container node-pools describe default-pool --cluster gke-trial
...
initialNodeCount: 3
...
```

##### ノードプールの変更
https://cloud.google.com/kubernetes-engine/docs/how-to/node-pools?hl=ja#resizing_a_node_pool

```
$ gcloud container clusters resize gke-trial --node-pool default-pool --num-nodes 1
Pool [default-pool] for [gke-trial] will be resized to 1.

Do you want to continue (Y/n)?  y

Resizing gke-trial...done.                                                     
Updated [https://container.googleapis.com/v1/projects/turnkey-rookery-323304/zones/asia-northeast1-a/clusters/gke-trial].

# ノードプールの詳細を確認し、サイズが1担ったことを確認
$ gcloud container node-pools describe default-pool --cluster gke-trial
...
initialNodeCount: 1
....
```

##### ノードのインスタンスタイプの変更
TODO

### Cloud Buildパイプラインの作成
https://cloud.google.com/build/docs/deploying-builds/deploy-gke?hl=ja
に基本従って進めていく。

#### GKE Developerを有効にする。
https://cloud.google.com/build/docs/deploying-builds/deploy-gke?hl=ja#required_iam_permissions

#### マニフェストファイルをGitHubリポジトリ内に用意しておく
`deployment/deployment.yml`に、以下をデプロイするよう記載。
- demo-spanner-jdbc-webのデプロイ
- 外部公開用のLoadBalancerのServiceのデプロイ
- (TODO)LoadBalancerのポリシー設定等のデプロイ

#### ビルド構成ファイルをGitHubリポジトリ内に用意しておく
`ci/cloudbuild.yml`に、以下を実行するよう記載。

- Javaのビルド(mvn)
  - https://cloud.google.com/build/docs/building/build-java?hl=ja
- コンテナのビルド(mvn)
- コンテナのContainer RegistryへのPush
- GKEへのデプロイ
  - https://cloud.google.com/build/docs/deploying-builds/deploy-gke?hl=ja#building_and_deploying_a_new_container_image


#### ローカルからパイプラインを試しに実行

```
$ gcloud builds submit --project=turnkey-rookery-323304 --config ci/cloudbuild.yml
```

### GitHubの操作をトリガに自動実行
#### GitHubリポジトリを接続
https://cloud.google.com/build/docs/deploying-builds/deploy-gke?hl=ja#automating_deployments
で、「リポジトリを接続」を選択。

GitHubで認証し、許可を与える。

初回は、「リポジトリを選択」で「GitHub アプリは、どのリポジトリにもインストールされていません」とエラーになっているので、
GitHubアプリ「Google Cloud Buildのインストール」のボタンを押す。

All repositoriesもしくは対象リポジトリを選択し、Install。

そのあとGCPに戻り、「トリガーを作成」を押す。

#### トリガを作成
https://cloud.google.com/build/docs/deploying-builds/deploy-gke?hl=ja#automating_deployments

##### トリガーの名前
トリガーの名前はいったん`gcp-trial-demo-spanner-jdbc-web`とする。

##### イベント
イベントは、「ブランチに push する」を選択する。
デフォルトの正規表現だと、mainブランチのみが対象となる。必要に応じて正規表現を変えてブランチ対象を増やす。

##### 構成
「Cloud Build 構成ファイル（yaml または json）」を選ぶ。
ロケーションは「ci/cloudbuild.yml」を入力する。

##### 代入変数
今回は使わないが、コミット番号をPodに記載させたり、ブランチごとにデプロイ先Podを分けたい場合などは利用が必要と思われる。

##### 作成
以上で「作成」する。

#### GitHubに任意ブランチをPush
適当に何か資材を修正し、GitHubに`git push`する。
ブラウザでCloud Buildの「履歴」を確認すると、新しいビルドが作成されている。

### インターネット経由でのAPI打鍵(H2接続)
前述で作成したパイプラインにより、GKEのLoadBalancerもデプロイされる。
デプロイされたロードバランサのグローバルIPをConsoleで確認（GKEの「サービス」から確認可能）し、curlでAPIを打鍵してみる。

この時点ではアプリケーションはPod内のH2に接続しているため、レプリカ数が2以上の場合は振り分けられたPod次第でデータ内容が変化する。


## GKEでの確認(GCPのSpannerへの接続)
### Spannerを有効にする
Console（ブラウザ）から有効にする。

https://console.cloud.google.com/flows/enableapi?apiid=spanner.googleapis.com&hl=ja&_ga=2.243158210.1164933426.1629795082-895740333.1618205695

### インスタンスの作成
https://cloud.google.com/spanner/docs/create-manage-instances?hl=ja
に従って、CLIベースで作成。検証用なので、単リージョンで100処理ユニット構成（＝0.1ノード相当、ベータ版）とする。

```
$ gcloud beta spanner instances create spanner-trial --config=regional-asia-northeast1 --description="spanner-trial" --processing-units=100
```

### データベースの作成
Spannerエミュレータでのデータベース作成と同様に作成可能。

```
gcloud spanner databases create test-database --instance=spanner-trial
```

### GKE -> Spannerへのアクセス権の付与
- https://qiita.com/atsumjp/items/9df1f4e18bea164f95fe
- https://medium.com/google-cloud-jp/k8s-gcp-access-controle-8d8e92446e84
    - 「k8s から GCP リソースへのアクセスを管理する」

に記載があるよう、GKE -> GCP（Spanner等）への鍵なしアクセスのためには、Workload Identityを使用し、Kubernatesサービスアカウント（KSA）とGCPサービスアカウント（GSA）を紐付付ける必要がある。

以降はGCPのリファレンスに従って作業をすすめる。

https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity?hl=ja#enable_on_cluster

#### 既存のGKEクラスタでWorkload Identity有効化
https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity?hl=ja#enable_on_cluster

```
$ gcloud container clusters update gke-trial --workload-pool=turnkey-rookery-323304.svc.id.goog
```
  
処理に結構時間を要する（5分くらい）。
  
#### 既存ノードプールでWorkload Identity有効化
https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity?hl=ja#option_2_node_pool_modification

```
$ gcloud container node-pools update default-pool --cluster=gke-trial --workload-metadata=GKE_METADATA
```  

処理に結構時間を要する（5分くらい）。

#### Google Cloud認証
https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity?hl=ja#authenticating_to

```
$ gcloud container clusters get-credentials gke-trial
$ kubectl create namespace trial
$ kubectl create serviceaccount --namespace trial ksa-trial
```

リンク先の通り、マニフェストに以下を追記する。
`serviceAccountName`はPodの`spec`の項目のため、kindがDeployment等の場合は、
`template.spec.serviceAccountName`に設定する。Serviceには該当項目がなく、権限も不要なため追加不要。

```
spec:
  serviceAccountName: ksa-trial
```

※ここでGKEにnamespace`trial`を作成しているので、デプロイするPod等のnamespaceも変更するる。

```
metadata:
  namespace: "trial"
```

本リポジトリの資材では、本項でのマニフェストファイル修正を`deployment-spanner.yml`に別ファイル化している。

#### Google Cloud認証（続き）

```
$ gcloud iam service-accounts create gsa-trial

$ gcloud iam service-accounts add-iam-policy-binding \
  --role roles/iam.workloadIdentityUser \
  --member "serviceAccount:turnkey-rookery-323304.svc.id.goog[trial/ksa-trial]" \
  gsa-trial@turnkey-rookery-323304.iam.gserviceaccount.com
```

アノテーション付与については、イミュータブルにすべく、
`kubectl`で実行ではなく、マニフェストに追加定義する。

```
apiVersion: v1
kind: ServiceAccount
metadata:
  annotations:
    iam.gke.io/gcp-service-account: gsa-trial@turnkey-rookery-323304.iam.gserviceaccount.com
  name: ksa-trial
  namespace: trial
```

#### GSAにSpannerのアクセス権を付与
作成したGSAに、SpannerのDBアクセス権を付与する。
以下の権限一覧より、「Cloud Spanner データベース ユーザー」`roles/spanner.databaseUser`を設定する。
これにより、マッピングされているKSAを用いて、PodがSpannerにアクセスできるようになる。
https://cloud.google.com/spanner/docs/iam/?hl=ja

アクセス権限の付与については以下の手順を参考に行う。
https://cloud.google.com/iam/docs/granting-changing-revoking-access?hl=ja#granting-gcloud-manual


```
$ gcloud projects add-iam-policy-binding turnkey-rookery-323304 \
    --member=serviceAccount:gsa-trial@turnkey-rookery-323304.iam.gserviceaccount.com --role=roles/spanner.databaseUser
```

### Spannerにテーブルを作成
サンプルアプリケーションでは、H2のような組込DB以外では起動時にテーブル作成DDLを実行しない設定にしているので、
テーブルを手動で作成する。

```
$ gcloud spanner databases ddl update test-database --ddl="CREATE TABLE todo(id STRING(36), title STRING(30), finished BOOL, created_at TIMESTAMP) PRIMARY KEY (id)"  --instance=spanner-trial
```


### Spannerへの接続先変更（プロファイル指定）
マニフェストにて、アプリケーションをデプロイするPodの環境変数を`env`で設定できる。
Springのプロファイル指定（`spring.profiles.active`）を、環境変数`SPRING_PROFILES_ACTIVE`経由で設定し、GCPのSpannerに接続するようにする。
`spanner-gcp`プロファイルは、エミュレータではなくGCPのSpannerに対しての接続設定がされているプロファイルである。

```
      containers:
      - name: "demo-spanner-jdbc-web"
        image: "gcr.io/turnkey-rookery-323304/demo-spanner-jdbc-web:latest"
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "spanner-gcp"
```

### インターネット経由でのAPI打鍵(Spanner接続)
長かったが、デプロイしたアプリケーションに対してAPIを打鍵※し、GCPのConsole等からSpannerにデータが格納されていることを確認する。

また、以前のようにPodの振り分け先の違いにより、API応答値のデータの変化が発生しないことも確認できるはず。

※前の作業で、GKEのネームスペースを変更しているため、以前の作業とは別Pod、別Serviceとしてデプロイされており、ロードバランサの接続先グローバルIPアドレスも変更になっている。


## ApigeeX経由でのAPI公開
https://cloud.google.com/apigee/docs/api-platform/tutorials/create-api-proxy-openapi-spec?hl=ja
にある、Open API Specification(Spec)を元にAPIプロキシを作成する手順を確認する。
サンプルアプリケーションのSpecを用意し、ApigeeXにインプットする。

### Open API Specの用意
今回、先にサンプルアプリケーションを作成済みのため、
[springdoc-openapi](https://springdoc.org/)でソースコードからSpecを生成し、
アプリケーションから公開されるSpecのエンドポイントを、インターネット経由でApigeeXにインプットする。

`pom.xml`に以下を追加し、サンプルアプリケーションにspringdoc-openapiを組み込む。

```
		<dependency>
			<groupId>org.springdoc</groupId>
			<artifactId>springdoc-openapi-ui</artifactId>
			<version>1.5.10</version>
			<scope>runtime</scope>
		</dependency>
```

サンプルアプリケーションを起動すると、以下のエンドポイントでSpec等が確認できる※。

- http://ホスト名/v3/api-docs
- http://ホスト名/v3/api-docs.yaml
- http://ホスト名/swagger-ui.html

※外部公開したくない場合は、以下のプロパティで無効化することが可能。
https://springdoc.org/#disabling-the-springdoc-openapi-endpoints

```
# Disabling the /v3/api-docs enpoint
springdoc.api-docs.enabled=false
# Disabling the swagger-ui
springdoc.swagger-ui.enabled=false
```

### Specの公開
ApigeeXでは、Webに公開されているSpecをインポート可能だが、
HTTPSでないと「Unexpected Error」が発生するため、今回の作成したでもアプリケーションの`/v3/api-docs.yaml`エンドポイント経由（HTTPではインポートができない。

そのため、`/v3/api-docs.yaml`エンドポイントから取得したSpec（YAMLファイル）を、GitHubリポジトリに格納し、GitHubからApigeeXにインポートする。

`/apispec`ディレクトリにSpecを格納する。

### APIプロキシの作成
#### Specのインポート
https://apigee.google.com/edge

の「Develop」→「API Proxies」の、「CREATE NEW」を押し、
「Reverse Procy(Most common)」の中の「Use OpenAPI Spec」を選択。

URLにGitHubのSpecのRawのURLを貼り付ける。

```
https://raw.githubusercontent.com/ikeyat/demo-spanner-jdbc-web/main/apispec/api-docs.yaml
```

#### Proxy Detail
Specの情報が入力されている。検証なのでそのままNextする。

#### Common Policies
検証のため、まずはミニマムの以下ですすめる。

- Pass throughを選択
- Add CORS headersチェック外す
- Quotaなし（そもそもPass Throughは選択不可）

#### OpenAPI operations
SpecのAPIが一覧されるので、確認してNextする。

#### Summary
これまでの設定が出るので必要に応じて確認。
「Optional Deployment」のチェックを入れてCreate（チェックするとデプロイが開始される？）。


### 外部ロードバランサの設定
ApigeeX開設時に外部ロードバランサ設定をSkipしている場合は、
APIプロキシをインターネット経由で打鍵できるよう、設定しておく。

https://cloud.google.com/apigee/docs/api-platform/get-started/configure-routing?hl=ja#external-access

- Enable Inernet accessを選択
- Use wildcard DNS serviceにチェックを入れる
  - ドメインを持ってない場合。検証目的なら無料でドメインが入手できる。
  - SSL証明書はGoogle Managed Certificationが自動的に作られ、適用される。
- Subnetworkは、defaultを選択

上記で作成すると、GCPのCloud Load Balancingにインスタンスが追加される。
そちらでグローバルIPが取得できる。

### APIプロキシの打鍵
https://cloud.google.com/apigee/docs/api-platform/tutorials/create-api-proxy-openapi-spec?hl=ja#testtheapiproxy

API打鍵を、以下のURLベースで行う。

```
https://<払い出されたグローバルIP>.nip.io/openapi-definition/todos
```

### ポリシーの適用（SpikeArrest）
https://cloud.google.com/apigee/docs/api-platform/tutorials/add-spike-arrest?hl=ja

上記URLを参考に、作成したAPIプロキシの`PreFlow`にSpikeArrestポリシーを適用し、制限値を`1ps`で設定し、
API打鍵を1秒に数回以上行うと、流量超過エラーが返されるのが確認できる。

```
$ # 1秒以内に複数回下記コマンドを実行
$ curl -D -  https://<払い出されたグローバルIP>.nip.io/openapi-definition/todos
HTTP/2 429 
content-type: application/json
x-request-id: f41e2756-d009-4e32-b74b-ae9f9e426a1d
content-length: 220
date: Fri, 27 Aug 2021 11:55:55 GMT
server: apigee
via: 1.1 google
alt-svc: clear

{"fault":{"faultstring":"Spike arrest violation. Allowed rate : MessageRate{messagesPerPeriod=1, periodInMicroseconds=1000000, maxBurstMessageCount=1.0}","detail":{"errorcode":"policies.ratelimit.SpikeArrestViolation"}}}%    
```

### ポリシーの適用（APIキー）
- https://cloud.google.com/apigee/docs/api-platform/security/api-keys?hl=ja
- https://cloud.google.com/apigee/docs/api-platform/security/setting-api-key-validation?hl=ja

APIキーを利用するための流れは、[大まかな手順](https://cloud.google.com/apigee/docs/api-platform/security/api-keys?hl=ja#howapikeyswork-highlevelsteps)にもあるよう以下となる。

- APIキーポリシーを適用したAPIプロキシの作成
- APIプロダクトの作成（APIプロキシのグルーピングと理解）
- デベロッパーの登録
- デベロッパーのクライアントアプリの登録
- APIキーを付けて、APIを打鍵

既存のAPIプロキシにAPIキーのポリシー追加することもできるが、
ここではSpecからAPIプロキシを作成し直してみる。

#### APIプロキシの作成
「APIプロキシの作成」を参照。
その中の「Common Policies」にて、Pass ThroughではなくAPI Keyを選択する。

#### APIプロダクトの作成
「Publish」の「API Products」へ進み「＋API Product」を押す。

「This API product is configured using a legacy format. Reconfigure to take advantage of new configuration features.」と表記されているので、右隣の「RECONFIGURE」を押す。

必須項目を以下のように埋める。

- Name / Display Nameは適当に決める。
- Environmentは、APIプロキシをデプロイした環境を選択する。
- Accessは「Internal Only」。デベロッパーには、デベロッパーとして登録が完了するまでAPIの存在すら見れないモードらしい。
- Allowed OAuth scopeはAPIキーでは使えないので空欄。
- API proxiesに、APIキーを適用したAPIプロキシを選択
- Pathsには、選択したAPIプロキシからさらに範囲をURLで狭めたい場合に設定する。狭める必要がなければ`/`を入力する。
- Methodsも狭めたい場合は選ぶ。狭めないなら全選択する。

#### Developerの作成
「Publish」の「Developers」へ進み「＋Developer」を押す。

ユーザ情報（FirstName、LastName、ユーザー名、Eメールアドレス）を入力。
このユーザはGCPのユーザとは全く関係ない（と思われる）。ApigeeXで作成したAPIの利用者ユーザ。

####クライアントアプリの登録
「Publish」の「Apps」へ進み「＋App」を押す。

- Nameは適当に決める。
- Developerは前手順で作成したものを選ぶ。
- CredentialsのExpiryはいったんNever。期限も受けたい場合は設定。
- Productには、前手順で作成したAPIプロダクトを選択。

上記で作成すると、APIキーとAPIクレデンシャルがブラウザ画面に出力される。
APIキー認証の場合は、APIキーのみを利用する。

#### APIの打鍵
以下のようにURLのクエリパラメータにAPIキーを付けることで、APIキーがApigeeXに送られ、ApigeeX側で認証が行われる。


```
https://<ホスト名>/todo-apikey/todos?apikey=<払い出されたAPIキー>
```

### ポリシーの適用（OAuth2ポリシー）
https://cloud.google.com/apigee/docs/api-platform/security/oauth/access-tokens?hl=ja#requestinganaccesstokenclientcredentialsgranttype

OAuth2のScopeを用いて、参照専用のアクセストークン、更新専用のアクセストークンを払い出し、
API打鍵する検証を行う。

OAuth2の場合は、以下のような流れとなる。

- OAuth2ポリシーを適用したAPIプロキシの作成
- トークンエンドポイントの作成
- APIプロダクトの作成（APIプロキシのグルーピングと理解）
- デベロッパーの登録
- デベロッパーのクライアントアプリの登録
- アクセストークンの払い出し
- アクセストークンを付けて、APIを打鍵

前項同様、既存のAPIプロキシにAPIキーのポリシー追加することもできるが、
ここではSpecからAPIプロキシを作成し直してみる。

なお、Open API Specification v.3では、[OAuth2](https://swagger.io/specification/#security-scheme-object)および、[各APIへのOAuth2の適用](https://swagger.io/specification/#security-requirement-object)についても定義可能であるが、ApigeeXのAPIプロキシ作成時に機能していないように見える（試した結果）ため、SpecからOAuth2を適用するのではなく、ApigeeX上でOAuth2を適用していく。

#### APIプロキシの作成

##### SpecからAPIプロキシを作成（既存APIプロキシ使用時はスキップ）
「APIプロキシの作成」を参照。
その中の「Common Policies」にて、Pass ThroughではなくOAuth2を選択する。

##### APIプロキシ作成後のポリシー適用初期状況の確認
ApigeeXで生成されたOAuth2ポリシーは初期では以下のような状態である。

- OAuth2トークンの検証ポリシーの定義
- 全APIに対し（`<PreFlow>`にて）、OAuth2トークンのアクセストークン検証ポリシーが適用（`OAuth`ポリシーの`VerifyAccessToken`オペレーション）
- 全APIに対し（`<PreFlow>`にて）、OAuth2トークンの削除ポリシーが適用（`AssignMessage`ポリシー）

ただ、以下の点で不完全なため、APIプロキシを修正する。

- Scopeが定義されていない。
  - 言い換えると、APIプロキシ対象の全APIに対し一律のアクセス制御しかできない。

##### OAuth2ポリシーの修正、追加
今回、参照専用、更新専用のアクセストークンを払い出せるようにするため、以下の2つのScopeを用意する。

- `todo_read`: 参照専用のScope
- `todo_write`: 更新専用のScope

初期状態で作られているOAuth2のアクセストークン検証ポリシーを修正し、参照専用Scopeであることの検証を加える。`<Scope>`が該当箇所となる。`name`や`DisplayName`も重複しないよう適宜変更する。

```
<OAuthV2 continueOnError="false" enabled="true" name="verify-oauth-v2-access-token-read">
    <DisplayName>Verify OAuth v2.0 Access Token Read</DisplayName>
    <Operation>VerifyAccessToken</Operation>
    <Scope>todo_read</Scope>
</OAuthV2>
```

更新専用Scopeのアクセストークン検証ポリシも必要なので、ポリシーを追加する。
Policiesの右の「+」ボタンを押し、「OAuth v2.0」を選択し、`name`や`DisplayName`を入力して追加」したあと、以下のように定義を修正する。

```
<OAuthV2 async="false" continueOnError="false" enabled="true" name="verify-oauth-v2-access-token-write">
    <DisplayName>Verify OAuth v2.0 Access Token Write</DisplayName>
    <Operation>VerifyAccessToken</Operation>
    <Scope>todo_write</Scope>
</OAuthV2>
```

##### OAuth2ポリシーの再適用
前項で修正、追加したOAuth2ポリシーを利用する側を修正する。

#### トークンエンドポイントの作成
ApigeeXがクライアントアプリケーションに対して、アクセストークンを払い出すための、トークンエンドポイントをProxy Endpointとして作成する。

ただし、[アンチパターン](https://cloud.google.com/apigee/docs/api-platform/antipatterns/multiple-proxyendpoints?hl=ja)に従い、
トークンエンドポイントのProxy Endpointは、別の専用のAPIプロキシを作成し、その中で定義する。

##### 新規APIプロキシの作成
「API Proxies」→「CREATE NEW」で、「No target」を選択する。


#### APIプロダクトの作成
「APIキー」ポリシー適用時と同様であるが、
Allowed OAuth Scopeに、前項で定義したscopeをカンマ区切りで入力する。

- Allowed OAuth Scope: `todo_read, todo_write`

#### Developerの作成
「APIキー」ポリシー適用時と同様のため略。

####クライアントアプリの登録
「APIキー」ポリシー適用時と同様のため略。


#### アクセストークンの払い出し（全スコープ）

アクセストークン払い出し時に必要な、クライアントクレデンシャルズ（キー＝クライアントID、シークレット）をBase64に変更する。キーとシークレットをコロン`:`で区切った文字列をBase64にする。

```
$ echo -n '＜キー＞:＜シークレット＞' | base64     
```

次に、アクセストークン払い出しをリクエストする。
まずはスコープを指定せず（＝全スコープを要求）でアクセストークンを払い出す。

```
$ curl -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic <Base64にしたクライアントクレデンシャルズ>" \
  -X POST "https://＜ホスト名＞/todo-oauth2-auth/token" \
  -d "grant_type=client_credentials"

{
  "refresh_token_expires_in": "0",
  "api_product_list": "[todo-oauth2-write, todo-oauth2-read, todo-apikey]",
  "api_product_list_json": [
    "todo-oauth2-write",
    "todo-oauth2-read",
    "todo-apikey"
  ],
  "organization_name": "turnkey-rookery-323304",
  "developer.email": "＜メールアドレス＞",
  "token_type": "BearerToken",
  "issued_at": "1630410924126",
  "client_id": "＜キー＞",
  "access_token": "＜アクセストークン＞",
  "application_name": "f997b0d8-1f43-4a2f-aecf-1507680dae22",
  "scope": "todo_read todo_write",
  "expires_in": "1799",
  "refresh_count": "0",
  "status": "approved"
}%         
```

#### APIの打鍵（認可OK）

```
$ curl -D - -H "Authorization: Bearer ＜アクセストークン＞" https://＜ホスト名＞/todo-oauth2/todos 
＜GET成功　レスポンス略＞

$ curl -D - -H "Authorization: Bearer ＜アクセストークン＞" -X POST -H "Content-Type: application/json" -d '{"title": "Hoge Hoge"}' https://＜ホスト名＞/todo-oauth2/todos
＜POST成功　レスポンス略＞
```

#### アクセストークンの払い出し（readスコープのみ）

改めて、スコープ`todo_read`を指定してアクセストークンを払い出す。
レスポンスより、アクセストークンのスコープが`todo_read`のみとなっていることが確認できる。

```
$ curl -H "Content-Type: application/x-www-form-urlencoded" \
  -H "Authorization: Basic <Base64にしたクライアントクレデンシャルズ>" \
  -X POST "https://＜ホスト名＞/todo-oauth2-auth/token" \
  -d "grant_type=client_credentials&scope=todo_read" 

{
  "refresh_token_expires_in": "0",
  "api_product_list": "[todo-oauth2-write, todo-oauth2-read, todo-apikey]",
  "api_product_list_json": [
    "todo-oauth2-write",
    "todo-oauth2-read",
    "todo-apikey"
  ],
  "organization_name": "turnkey-rookery-323304",
  "developer.email": "＜メールアドレス＞",
  "token_type": "BearerToken",
  "issued_at": "1630412050704",
  "client_id": "＜キー＞",
  "access_token": "＜アクセストークン＞",
  "application_name": "f997b0d8-1f43-4a2f-aecf-1507680dae22",
  "scope": "todo_read",
  "expires_in": "1799",
  "refresh_count": "0",
  "status": "approved"
}%
```

#### APIの打鍵（認可NG）

```
$ curl -D - -H "Authorization: Bearer ＜アクセストークン＞" https://＜ホスト名＞/todo-oauth2/todos 
＜GET成功　レスポンス略＞

$ curl -D - -H "Authorization: Bearer ＜アクセストークン＞" -X POST -H "Content-Type: application/json" -d '{"title": "Hoge Hoge"}' https://＜ホスト名＞/todo-oauth2/todos
＜POST失敗　レスポンス略＞
```



