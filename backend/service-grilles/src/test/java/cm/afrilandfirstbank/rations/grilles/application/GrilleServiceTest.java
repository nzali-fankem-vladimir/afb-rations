package cm.afrilandfirstbank.rations.grilles.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import cm.afrilandfirstbank.rations.commun.audit.EvenementAudit;
import cm.afrilandfirstbank.rations.commun.audit.PublicateurAudit;
import cm.afrilandfirstbank.rations.grilles.api.dto.CreationGrilleRequest;
import cm.afrilandfirstbank.rations.grilles.domaine.GrilleTarifaire;
import cm.afrilandfirstbank.rations.grilles.domaine.NatureEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.SessionEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.StatutGrilleEnum;
import cm.afrilandfirstbank.rations.grilles.domaine.exception.ConflitGrilleException;
import cm.afrilandfirstbank.rations.grilles.infrastructure.GrilleTarifaireRepository;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.AuteurIdentifie;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.ClientIdentite;
import cm.afrilandfirstbank.rations.grilles.infrastructure.identite.IdentiteIndisponibleException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Creation et soumission d'une grille par l'Analyste RH (Sprint 2.2).
 *
 * <p>Couvre les six tests unitaires du guide, dont le sixieme — la conformite aux
 * deux decisions prises en cours de sprint — porte sur ce qui distingue ce
 * sous-sprint d'une lecture naive du contrat d'API.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Creation et soumission d'une grille (US-13)")
class GrilleServiceTest {

    private static final String JETON = "Bearer jeton-de-test";
    private static final String IP = "10.12.4.31";
    private static final AuteurIdentifie CLAIRE_NKOLO =
            new AuteurIdentifie(4L, "claire_nkolo", "NKOLO", "Claire");

    @Mock
    private GrilleTarifaireRepository grilleTarifaireRepository;

    @Mock
    private UniciteGrilleService uniciteGrilleService;

    @Mock
    private ClientIdentite clientIdentite;

    @Mock
    private PublicateurAudit publicateurAudit;

    @InjectMocks
    private GrilleService grilleService;

    private static CreationGrilleRequest proposition(NatureEnum nature, SessionEnum session,
            Integer montant, LocalDate dateDebut) {
        return new CreationGrilleRequest(nature, session, montant, dateDebut);
    }

    /** Le repository rend la grille telle qu'on la lui a confiee. */
    private void enregistrementRendLaGrille() {
        when(grilleTarifaireRepository.save(any(GrilleTarifaire.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("1. Creation nominale")
    class Nominale {

        @Test
        @DisplayName("couple sans grille active : acceptee, au statut EN_ATTENTE_DRH")
        void creationAcceptee() {
            when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
            enregistrementRendLaGrille();

            GrilleTarifaire creee = grilleService.creerEtSoumettre(
                    proposition(NatureEnum.TRANSPORT, SessionEnum.SOIR, 3000, LocalDate.of(2026, 9, 1)),
                    JETON, IP);

            assertThat(creee.getStatutValidation()).isEqualTo(StatutGrilleEnum.EN_ATTENTE_DRH);
            assertThat(creee.getNature()).isEqualTo(NatureEnum.TRANSPORT);
            assertThat(creee.getSession()).isEqualTo(SessionEnum.SOIR);
            assertThat(creee.getMontantFcfa()).isEqualTo(3000);
            assertThat(creee.getDateDebut()).isEqualTo(LocalDate.of(2026, 9, 1));
        }

        @Test
        @DisplayName("l'auteur resolu est inscrit dans la grille, identifiant ET libelle")
        void auteurInscrit() {
            when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
            enregistrementRendLaGrille();

            GrilleTarifaire creee = grilleService.creerEtSoumettre(
                    proposition(NatureEnum.RATION, SessionEnum.JOUR, 1800, LocalDate.of(2026, 10, 1)),
                    JETON, IP);

            // L'identifiant est la donnee de reference ; le libelle est la copie
            // figee qui evite un appel reseau a chaque lecture (decision Sprint 2.2).
            assertThat(creee.getIdCreateur()).isEqualTo(4L);
            assertThat(creee.getLibelleCreateur()).isEqualTo("NKOLO Claire");

            // Rien n'est encore decide : la grille n'a ni validateur ni date de fin.
            assertThat(creee.getIdValidateur()).isNull();
            assertThat(creee.getLibelleValidateur()).isNull();
            assertThat(creee.getDateFin()).isNull();
        }

        @Test
        @DisplayName("la creation ET la soumission sont tracees dans le journal d'audit")
        void deuxEvenementsPublies() {
            when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
            enregistrementRendLaGrille();

            grilleService.creerEtSoumettre(
                    proposition(NatureEnum.TRANSPORT, SessionEnum.SOIR, 3000, LocalDate.of(2026, 9, 1)),
                    JETON, IP);

            ArgumentCaptor<EvenementAudit> captureur = ArgumentCaptor.forClass(EvenementAudit.class);
            verify(publicateurAudit, times(2)).publier(captureur.capture());

            List<EvenementAudit> evenements = captureur.getAllValues();
            assertThat(evenements).extracting(EvenementAudit::action)
                    .containsExactly("CREATION_GRILLE", "SOUMISSION_GRILLE");
            assertThat(evenements).allSatisfy(evenement -> {
                assertThat(evenement.idUtilisateur()).isEqualTo(4L);
                assertThat(evenement.entiteCible()).isEqualTo("grille_tarifaire");
                assertThat(evenement.adresseIp()).isEqualTo(IP);
                assertThat(evenement.detailJson()).isNotBlank();
            });
        }
    }

    @Nested
    @DisplayName("2 et 3. Conflits d'unicite")
    class Conflits {

        @Test
        @DisplayName("2. couple deja couvert par une grille ACTIVE : refusee avec le code de conflit")
        void conflitGrilleActive() {
            when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
            doThrow(new ConflitGrilleException(ConflitGrilleException.CODE_GRILLE_ACTIVE,
                    "Une grille RATION / JOUR est active depuis le 01/08/2026."))
                    .when(uniciteGrilleService).verifierAvantCreation(any(), any(), any());

            assertThatThrownBy(() -> grilleService.creerEtSoumettre(
                    proposition(NatureEnum.RATION, SessionEnum.JOUR, 1800, LocalDate.of(2026, 8, 1)),
                    JETON, IP))
                    .isInstanceOf(ConflitGrilleException.class)
                    .extracting(erreur -> ((ConflitGrilleException) erreur).getCode())
                    .isEqualTo("GRILLE_ACTIVE_EXISTANTE");

            // Refus tot : rien n'est enregistre, rien n'est trace comme une action
            // aboutie (document maitre section 7.3).
            verify(grilleTarifaireRepository, never()).save(any());
            verify(publicateurAudit, never()).publier(any());
        }

        @Test
        @DisplayName("3. couple deja EN_ATTENTE_DRH : refusee")
        void conflitPropositionEnAttente() {
            when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
            doThrow(new ConflitGrilleException(ConflitGrilleException.CODE_PROPOSITION_EN_ATTENTE,
                    "Une grille TRANSPORT / SOIR attend deja la decision de la Directrice RH."))
                    .when(uniciteGrilleService).verifierAvantCreation(any(), any(), any());

            assertThatThrownBy(() -> grilleService.creerEtSoumettre(
                    proposition(NatureEnum.TRANSPORT, SessionEnum.SOIR, 3200, LocalDate.of(2026, 11, 1)),
                    JETON, IP))
                    .isInstanceOf(ConflitGrilleException.class)
                    .extracting(erreur -> ((ConflitGrilleException) erreur).getCode())
                    .isEqualTo("GRILLE_EN_ATTENTE_EXISTANTE");

            verify(grilleTarifaireRepository, never()).save(any());
        }
    }

    /**
     * Tests 4 et 5 : la validation du DTO d'entree.
     *
     * <p>Elle est verifiee ici sur le {@code Validator} lui-meme, et non au travers
     * du service : ces refus interviennent AVANT que le service ne soit appele,
     * puisque le controleur porte {@code @Valid}. Les tester sur le service
     * donnerait un faux sentiment de couverture — le service, lui, ne verifie pas
     * le montant.
     */
    @Nested
    @DisplayName("4 et 5. Validation du DTO d'entree")
    class ValidationEntree {

        // La fabrique n'est volontairement pas fermee : le Validator qu'elle
        // produit s'appuie sur elle, et la fermer ici rendrait les validations
        // suivantes imprevisibles. Elle vit le temps de la classe de test.
        private static final ValidatorFactory FABRIQUE = Validation.buildDefaultValidatorFactory();
        private static final Validator validateur = FABRIQUE.getValidator();

        private Set<ConstraintViolation<CreationGrilleRequest>> valider(CreationGrilleRequest requete) {
            return validateur.validate(requete);
        }

        @Test
        @DisplayName("4a. montant negatif : refuse")
        void montantNegatifRefuse() {
            assertThat(valider(proposition(NatureEnum.RATION, SessionEnum.JOUR, -100, LocalDate.of(2026, 9, 1))))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("montantFcfa");
        }

        @Test
        @DisplayName("4b. montant nul : refuse")
        void montantNulRefuse() {
            // Zero n'est pas un tarif : une prestation servie gratuitement ne se
            // decrit pas par une grille a 0 FCFA, elle ne se saisit pas.
            assertThat(valider(proposition(NatureEnum.RATION, SessionEnum.JOUR, 0, LocalDate.of(2026, 9, 1))))
                    .isNotEmpty();
        }

        @Test
        @DisplayName("4c. montant absent : refuse, avec un message distinct de celui du montant nul")
        void montantAbsentRefuse() {
            Set<ConstraintViolation<CreationGrilleRequest>> violations =
                    valider(proposition(NatureEnum.RATION, SessionEnum.JOUR, null, LocalDate.of(2026, 9, 1)));

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).contains("obligatoire");
        }

        @Test
        @DisplayName("5a. nature absente : refusee")
        void natureAbsenteRefusee() {
            assertThat(valider(proposition(null, SessionEnum.JOUR, 1500, LocalDate.of(2026, 9, 1))))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("nature");
        }

        @Test
        @DisplayName("5b. session absente : refusee")
        void sessionAbsenteRefusee() {
            assertThat(valider(proposition(NatureEnum.RATION, null, 1500, LocalDate.of(2026, 9, 1))))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("session");
        }

        @Test
        @DisplayName("5c. date de debut absente : refusee")
        void dateDebutAbsenteRefusee() {
            assertThat(valider(proposition(NatureEnum.RATION, SessionEnum.JOUR, 1500, null)))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .containsExactly("dateDebut");
        }

        @Test
        @DisplayName("proposition complete et coherente : aucune violation")
        void propositionValide() {
            assertThat(valider(proposition(NatureEnum.TRANSPORT, SessionEnum.SOIR, 3000,
                    LocalDate.of(2026, 9, 1)))).isEmpty();
        }
    }

    @Nested
    @DisplayName("6. Conformite aux decisions des etapes 1 et 4")
    class ConformiteAuxDecisions {

        @Test
        @DisplayName("etape 1 : une proposition sur un couple actif cree une NOUVELLE ligne, sans toucher l'ancienne")
        void versionnementParNouvelleLigne() {
            when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
            enregistrementRendLaGrille();

            grilleService.creerEtSoumettre(
                    proposition(NatureEnum.RATION, SessionEnum.JOUR, 1800, LocalDate.of(2026, 10, 1)),
                    JETON, IP);

            ArgumentCaptor<GrilleTarifaire> captureur = ArgumentCaptor.forClass(GrilleTarifaire.class);
            verify(grilleTarifaireRepository).save(captureur.capture());

            // La ligne enregistree est neuve : elle n'a pas d'identifiant, donc
            // c'est une insertion, jamais la mise a jour d'une grille existante.
            assertThat(captureur.getValue().getId()).isNull();
            assertThat(captureur.getValue().getMontantFcfa()).isEqualTo(1800);

            // Aucune lecture-modification d'une grille existante : le service ne
            // charge meme pas l'ancienne.
            verify(grilleTarifaireRepository, never()).findById(any());
        }

        @Test
        @DisplayName("etape 4 : ce qui atteint la base est deja EN_ATTENTE_DRH, jamais BROUILLON")
        void aucunBrouillonPersiste() {
            when(clientIdentite.resoudreAuteur(JETON)).thenReturn(CLAIRE_NKOLO);
            enregistrementRendLaGrille();

            grilleService.creerEtSoumettre(
                    proposition(NatureEnum.TRANSPORT, SessionEnum.JOUR, 1200, LocalDate.of(2026, 9, 15)),
                    JETON, IP);

            ArgumentCaptor<GrilleTarifaire> captureur = ArgumentCaptor.forClass(GrilleTarifaire.class);
            verify(grilleTarifaireRepository).save(captureur.capture());

            assertThat(captureur.getValue().getStatutValidation())
                    .isEqualTo(StatutGrilleEnum.EN_ATTENTE_DRH);

            // Un seul enregistrement : la soumission n'est pas un second aller-retour
            // en base, elle a lieu avant l'insertion.
            verify(grilleTarifaireRepository, times(1)).save(any());
        }
    }

    @Nested
    @DisplayName("Refus conservateur sur panne du service Identite")
    class PanneIdentite {

        @Test
        @DisplayName("service Identite injoignable : rien n'est enregistre")
        void panneRefuseLaCreation() {
            when(clientIdentite.resoudreAuteur(anyString()))
                    .thenThrow(new IdentiteIndisponibleException("Delai depasse."));

            assertThatThrownBy(() -> grilleService.creerEtSoumettre(
                    proposition(NatureEnum.RATION, SessionEnum.JOUR, 1800, LocalDate.of(2026, 10, 1)),
                    JETON, IP))
                    .isInstanceOf(IdentiteIndisponibleException.class);

            // Aucune grille anonyme n'est creee en repli : une piece de controle
            // interne sans auteur ne vaut rien (doctrine Sprint 1.3).
            verify(grilleTarifaireRepository, never()).save(any());
            verify(uniciteGrilleService, never()).verifierAvantCreation(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("Consultation")
    class Consultation {

        @Test
        @DisplayName("sans tri demande : nature, puis session, puis date de debut decroissante")
        void triParDefautApplique() {
            when(grilleTarifaireRepository.rechercherParStatut(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            grilleService.lister(null, PageRequest.of(0, 20));

            ArgumentCaptor<Pageable> captureur = ArgumentCaptor.forClass(Pageable.class);
            verify(grilleTarifaireRepository).rechercherParStatut(any(), captureur.capture());

            assertThat(captureur.getValue().getSort()).containsExactly(
                    Sort.Order.asc("nature"),
                    Sort.Order.asc("session"),
                    Sort.Order.desc("dateDebut"));
        }

        @Test
        @DisplayName("tri explicite de l'appelant : conserve, pas ecrase")
        void triExpliciteConserve() {
            when(grilleTarifaireRepository.rechercherParStatut(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));
            Sort triDemande = Sort.by(Sort.Order.desc("montantFcfa"));

            grilleService.lister(StatutGrilleEnum.EN_ATTENTE_DRH, PageRequest.of(1, 5, triDemande));

            ArgumentCaptor<Pageable> captureur = ArgumentCaptor.forClass(Pageable.class);
            verify(grilleTarifaireRepository).rechercherParStatut(any(), captureur.capture());

            assertThat(captureur.getValue().getSort()).isEqualTo(triDemande);
            assertThat(captureur.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captureur.getValue().getPageSize()).isEqualTo(5);
        }

        @Test
        @DisplayName("le filtre de statut est transmis tel quel au repository")
        void filtreStatutTransmis() {
            when(grilleTarifaireRepository.rechercherParStatut(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            grilleService.lister(StatutGrilleEnum.EN_ATTENTE_DRH, PageRequest.of(0, 10));

            verify(grilleTarifaireRepository)
                    .rechercherParStatut(org.mockito.ArgumentMatchers.eq(StatutGrilleEnum.EN_ATTENTE_DRH),
                            any(Pageable.class));
        }

        @Test
        @DisplayName("la consultation n'appelle jamais le service Identite")
        void consultationSansAppelReseau() {
            when(grilleTarifaireRepository.rechercherParStatut(any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            grilleService.lister(null, PageRequest.of(0, 20));

            // C'est ce qui garantit qu'une panne du service Identite empeche de
            // creer une grille, jamais d'en consulter (decision Sprint 2.2).
            verify(clientIdentite, never()).resoudreAuteur(anyString());
        }
    }

}
