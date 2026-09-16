package cm.afrilandfirstbank.rations.saisie.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.saisie.infrastructure.LignePrestationRepository;

/**
 * Garde de construction du sous-sprint 6bis.2 : <b>RG-15 ne fait aucun appel
 * reseau</b>, et le controle de doublon ne lit jamais le statut d'un processus.
 *
 * <h2>Pourquoi une garde plutot qu'un commentaire</h2>
 *
 * <p>Le guide 6bis.2 prescrivait un endpoint cote Workflow et un appel
 * inter-services <b>par ligne saisie</b>. On s'en passe, et l'argument tient en
 * une phrase : la <b>contrainte d'exclusion</b> de la Maille 1 garantit que
 * « les etats de l'unite qui couvrent cette journee » et « les etats de l'unite
 * sur cette periode » sont le meme ensemble, or {@code code_unite} et
 * {@code date_jour} sont deja sur la fiche (Sprint 3.1).
 *
 * <p>C'est un raisonnement, donc quelque chose qu'un futur lecteur peut ne pas
 * connaitre. Rien n'empecherait alors d'ajouter « juste une verification » aupres
 * du service Workflow — ce qui rendrait la saisie d'une ligne dependante d'un
 * quatrieme service, sur un chemin qui en compte deja trois pour une cible de
 * 3 secondes (Sprint 3.2). Meme discipline que {@code PerimetreDuModuleTest}
 * (Sprint 1.3) et {@code CleInterneJamaisJournaliseeTest} (Sprint 5.2) : ce qui
 * doit rester vrai est verifie au build, pas confie a la memoire.
 *
 * <p><b>Cette garde n'interdit rien d'utile.</b> Si un jour RG-15 doit vraiment
 * consulter le Workflow — parce que la contrainte d'exclusion aurait ete
 * relachee, par exemple —, ce test echouera, et c'est exactement le moment ou il
 * faut relire le raisonnement ci-dessus avant de le supprimer.
 */
@DisplayName("RG-15 — controle local, aucun appel reseau")
class Rg15SansAppelReseauTest {

    private static final Path SOURCE_CONTROLE =
            Path.of("src/main/java/cm/afrilandfirstbank/rations/saisie/application",
                    "ControleDoublonService.java");

    @Test
    @DisplayName("le service de controle ne depend que du repository des lignes")
    void aucuneDependanceSortante() {
        Constructor<?>[] constructeurs = ControleDoublonService.class.getDeclaredConstructors();

        assertThat(constructeurs)
                .as("un seul constructeur, pour qu'il n'y ait qu'une porte a surveiller")
                .hasSize(1);

        assertThat(constructeurs[0].getParameterTypes())
                .as("toute dependance ajoutee ici serait le retour de l'appel reseau evite")
                .containsExactly(LignePrestationRepository.class);
    }

    @Test
    @DisplayName("aucun client HTTP n'est nomme dans la source du controle")
    void aucunClientDansLaSource() throws IOException {
        String source = Files.readString(SOURCE_CONTROLE);

        // Les ports sortants du service Saisie suivent tous le meme suffixe :
        // VerificationProcessusClient, HabilitationClient, ResolutionMontantClient,
        // PorteeClient. Aucun ne doit apparaitre ici, fut-ce en appel indirect.
        assertThat(lignesDeCode(source))
                .as("RG-15 se resout en base ; un client sortant ici trahirait un appel par ligne saisie")
                .noneMatch(ligne -> ligne.contains("Client"));
    }

    @Test
    @DisplayName("le statut du processus n'entre pas dans le controle")
    void aucunStatutDeProcessus() throws IOException {
        String source = Files.readString(SOURCE_CONTROLE);

        // L'arbitrage du sous-sprint : les etats EN_COURS_SAISIE et RETOURNE sont
        // INCLUS. Cette inclusion n'est pas un filtre — c'est l'absence de filtre,
        // et c'est elle qui rend le controle realisable sans appeler le Workflow,
        // seul detenteur du statut. Filtrer un jour sur le statut ferait donc
        // deux choses a la fois : changer la regle, et reintroduire l'appel.
        assertThat(lignesDeCode(source))
                .as("filtrer sur le statut changerait la regle ET reintroduirait l'appel reseau")
                .noneMatch(ligne -> ligne.contains("StatutProcessusEnum"));
    }

    /**
     * Les lignes de code seules : les blocs de commentaire et la javadoc parlent
     * abondamment de ce qui a ete ecarte, et une garde qui les lirait echouerait
     * sur sa propre justification.
     */
    private static java.util.List<String> lignesDeCode(String source) {
        return Arrays.stream(source.split("\n"))
                .map(String::strip)
                .filter(ligne -> !ligne.startsWith("*") && !ligne.startsWith("/*")
                        && !ligne.startsWith("//"))
                .toList();
    }

}
