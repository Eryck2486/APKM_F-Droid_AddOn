package com.apkm.addon.fdroid;

import android.app.Notification;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import com.google.gson.Gson;

public class Provider extends ContentProvider {
    // Classes para o Gson gerar o JSON idêntico ao que o apkm espera
    static class PacoteAddon {
        String pacote;
        String nome;
        String descricao;
        String versao;
        String sha256sumOrPGPLink;
        String endereco;
        List<String> arquiteturas;
        String icon;
        PacoteAddon(String pacote, String nome, String descricao, String versao, String sha256sum, String endereco, List<String> arquiteturas, String icon) {
            this.pacote = pacote;
            this.nome = nome;
            this.descricao = descricao;
            this.versao = versao;
            this.sha256sumOrPGPLink = sha256sum;
            this.endereco = endereco;
            this.arquiteturas = arquiteturas;
            this.icon = icon;
        }
        PacoteAddon() {
        }
    }

    static class RepositorioEstatico {
        //Nome do repositório
        String name;
        //Nome da origem que deve aparecer no gerenciador de pacotes
        String origem;
        //Local onde o gerenciador deve encontrar o arquivo APK
        String repository_sources_path;
        //Lista de pacotes que serão adicionados ao repositório
        List<PacoteAddon> packages = new ArrayList<>();
        //Lista de hashs de certificados SSL que são válidos para o repositório para validação da fonte
        List<String> pinned_hashs = new ArrayList<>();
    }
    static class Configs
    {
        public String versao;
        public String fornecedor;
        public boolean dinamico;
        public String nomeExibicao;
        public String descricao;
        public String prefix;
        //
        public static volatile String SOCKET_NAME = "UKNOWN";
        public static volatile boolean hasUpdate = false;
        public String SocketName;
        Configs(boolean dinamico, String nomeExibicao, String descricao, String versao, String fornecedor, String prefix){
            this.dinamico = dinamico;
            this.nomeExibicao = nomeExibicao;
            this.descricao = descricao;
            this.hasUpdate = hasUpdate;
            this.versao = versao;
            this.fornecedor = fornecedor;
            this.prefix = prefix;
            this.SocketName = Provider.Configs.SOCKET_NAME;
        }
    }


    static class SearchResult
    {
        String packageID;
        String iconBase64;
        String name;
        String description; //description/summary
        String version;
        String packageLicense;
        List<String> archs;
        String pgpLink; //sha256 or pgp link
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        //Garantindo que o serviço está inicializado
        String appNome;
        String versao;
        try {
            appNome = Objects.requireNonNull(getContext()).getApplicationInfo().loadLabel(getContext().getPackageManager()).toString();
            versao = Objects.requireNonNull(getContext()).getPackageManager().getPackageInfo(getContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        Gson gson = new Gson();
        String comando = uri.getLastPathSegment();
        String jsonresposta="NULL";
        // Criamos as colunas que o C++ espera
        MatrixCursor cursor = new MatrixCursor(new String[]{"json_data"});
        assert comando != null;
        Log.i("Commando:", comando);
        if(comando.equals("getRepos")) {
            //Função utilizada somente se o AddOn for estático
            //Lógica para o comando getRepo para AddOns estáticos
            //Criando um exemplo de repositório estático
            //Criando exemplo de repositório estático:
            RepositorioEstatico repositorio = new RepositorioEstatico();
            repositorio.name = "exemplo"; //Nome do repositório
            repositorio.repository_sources_path = "https://raw.githubusercontent.com/Eryck2486/apkm/refs/heads/main/exemplo_repositorio_root/"; //Onde ficam salvos os APKs
            //Hashs de exemplo, podem não ser mais válidos no momento
            repositorio.pinned_hashs.add("7d1122ea969852341e8dd92bcc0c7ecc009630d14da734d7ca42d5b54a2b2097"); //Hash do certificado folha
            repositorio.pinned_hashs.add("7d1122ea969852341e8dd92bcc0c7ecc009630d14da734d7ca42d5b54a2b2097"); //Hash de certificados untrusted vinculados
            repositorio.pinned_hashs.add("7fa4ff68ec04a99d7528d5085f94907f4d1dd1c5381bacdc832ed5c960214676"); //Hash de certificados untrusted vinculados
            repositorio.pinned_hashs.add("68b9c761219a5b1f0131784474665db61bbdb109e00f05ca9f74244ee5f5f52b"); //Hash de certificados untrusted vinculados
            //Nome que aparecerá no pull de repositórios
            repositorio.origem = appNome; //Recomenda-se colocar o nome do App aqui, nesse exemplo aparecerá "Origem: APKM F-Droid AddOn (Static AddOn)"
            repositorio.packages.add(new PacoteAddon("com.termux","Termux","Termux combina uma poderosa emulação de terminal com uma extensa coleção de pacote Linux.", "0.118.3", "fdd476982cd74f2f00aac12d3683b1fa260a0b2d146411b94e09d773be3a7b56", "https://f-droid.org/repo/com.termux_1022.apk", new ArrayList<String>(Arrays.asList(new String[]{"all"})), ""));
            repositorio.packages.add(new PacoteAddon("com.app.exemplo", "Exemplo app", "Este app faz absolutamente nada, divirta-se", "1.0", "b50c1ad2c537094b438f456745e1473c", "com.app/exemplo.apk", new ArrayList<String>(Arrays.asList(new String[]{"all"})), ""));
            jsonresposta = gson.toJson(repositorio);
            cursor.addRow(new Object[]{jsonresposta});
        }else if(comando.equals("getConfig")) {
            //Retorna as configurações do AddOn
            String fornecedor = "Eryck2486";
            Configs configs = new Configs(true, appNome, "AddOn do FDroid para o gerenciador de pacotes APKM.", versao, fornecedor, getContext().getString(R.string.prefix));
            jsonresposta = gson.toJson(configs);
            cursor.addRow(new Object[]{jsonresposta});
        }
        return cursor;
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "text/plain"; }
    // Métodos de insert/delete/update podem ficar vazios ou retornar null
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
