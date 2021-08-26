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
TODO