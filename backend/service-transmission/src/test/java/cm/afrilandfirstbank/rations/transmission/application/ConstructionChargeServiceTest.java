package cm.afrilandfirstbank.rations.transmission.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import cm.afrilandfirstbank.rations.transmission.application.ResultatConstruction.ChargeConstruite;
import cm.afrilandfirstbank.rations.transmission.application.ResultatConstruction.ChargeRefusee;
import cm.afrilandfirstbank.rations.transmission.domaine.CodeAnomalieEnum;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent;
import cm.afrilandfirstbank.rations.transmission.domaine.EtatValideEvent.LigneEtat;

/**
 * Le dernier filet avant la comptabilite.
 *
 * <p>Ces tests ne verifient pas un comportement d'affichage : ils verifient qu'aucune
 * charge fausse ne peut partir. Une fois l'evenement publie sur
 * {@code rations.etat.valide}, le module n'a aucun moyen de le rattraper — le module de
 * comptabilisation fabrique ses ecritures a partir de ce qu'il a recu, et l'equipe n'y a
 * pas acces.
 *
 * <p>Trois familles s'y trouvent, dans l'ordre de leur gravite :
 * <ol>
 *   <li>la charge conforme, champ par champ contre le contrat section 7.1 ;</li>
 *   <li>la coherence du montant total, sur plusieurs journees et plusieurs beneficiaires ;</li>
 *   <li>le refus de tout ce qui est incomplet, avec l'anomalie exacte attendue.</li>
 * </ol>
 */
class ConstructionChargeServiceTest {

    private final ConstructionChargeService service = new ConstructionChargeService();

    // --- Jeu de donnees ----------------------------------------------------------

    private static final String UNITE = "00002";
    private static final int MOIS = 7;
    private static final int ANNEE = 2026;

    /** Les bornes de la periode d'essai — juillet 2026, un mois entier (Maille 1). */
    /** Le compte de charge, tel que la migration V7 le pose (Maille 2). */
    private static final String COMPTE_CHARGE = "64380090200";

    private static final LocalDate DEBUT = LocalDate.of(ANNEE, MOIS, 1);
    private static final LocalDate FIN = LocalDate.of(ANNEE, MOIS, 31);
    private static final long ID_PROCESSUS = 740L;

    private static EnTeteProcessus enTete(int montantTotal) {
        return new EnTeteProcessus(ID_PROCESSUS, "CLOTURE", UNITE, DEBUT, FIN, COMPTE_CHARGE, "NORMAL",
                montantTotal, false);
    }

    private static EtatConsolide.Beneficiaire beneficiaire(String nom, String prenom,
            String compte, String codeAgence) {
        return new EtatConsolide.Beneficiaire(1L, nom, prenom, compte, codeAgence);
    }

    private static EtatConsolide.Ligne ligne(EtatConsolide.Beneficiaire beneficiaire,
            String nature, String session, Integer montant) {
        return new EtatConsolide.Ligne(1L, beneficiaire.id(), beneficiaire, nature, session,
                montant);
    }

    private static EtatConsolide.Journee journee(LocalDate jour,
            List<EtatConsolide.Ligne> lignes) {
        long sousTotal = lignes.stream()
                .mapToLong(l -> l.montantApplique() == null ? 0 : l.montantApplique())
                .sum();
        return new EtatConsolide.Journee(jour.toEpochDay(), jour, "ENREGISTREE", lignes.size(),
                sousTotal, lignes);
    }

    /** Etat consolide dont le total est celui, reel, des lignes fournies. */
    private static EtatConsolide etat(List<EtatConsolide.Journee> journees) {
        long total = journees.stream()
                .flatMap(j -> j.lignes().stream())
                .mapToLong(l -> l.montantApplique() == null ? 0 : l.montantApplique())
                .sum();
        int nombreLignes = journees.stream().mapToInt(j -> j.lignes().size()).sum();

        return new EtatConsolide(ID_PROCESSUS, UNITE, DEBUT, FIN, journees.size(), nombreLignes,
                nombreLignes, total, journees);
    }

    /**
     * Deux journees, deux beneficiaires, quatre lignes couvrant les deux natures et les
     * deux sessions. Total : 2500 + 1500 + 2500 + 1000 = 7500 FCFA.
     */
    private static List<EtatConsolide.Journee> deuxJourneesQuatreLignes() {
        EtatConsolide.Beneficiaire mbarga = beneficiaire("Mbarga", "Jean", "00002000123456", "00002");
        EtatConsolide.Beneficiaire nkolo = beneficiaire("Nkolo", "Alice", "00003000987654", "00003");

        return List.of(
                journee(LocalDate.of(ANNEE, MOIS, 3), List.of(
                        ligne(mbarga, "RATION", "JOUR", 2500),
                        ligne(nkolo, "TRANSPORT", "SOIR", 1500))),
                journee(LocalDate.of(ANNEE, MOIS, 4), List.of(
                        ligne(mbarga, "RATION", "SOIR", 2500),
                        ligne(nkolo, "TRANSPORT", "JOUR", 1000))));
    }

    private static final int TOTAL_ATTENDU = 7500;

    private static List<CodeAnomalieEnum> codes(ResultatConstruction resultat) {
        assertThat(resultat).isInstanceOf(ChargeRefusee.class);
        return ((ChargeRefusee) resultat).anomalies().stream().map(AnomalieCharge::code).toList();
    }

    private static EtatValideEvent charge(ResultatConstruction resultat) {
        assertThat(resultat)
                .withFailMessage("Charge refusee alors qu'elle etait attendue conforme : %s",
                        resultat)
                .isInstanceOf(ChargeConstruite.class);
        return ((ChargeConstruite) resultat).charge();
    }

    // --- 1. Conformite au contrat ------------------------------------------------

    @Nested
    @DisplayName("Charge conforme au contrat d'API section 7.1")
    class ChargeConforme {

        @Test
        @DisplayName("la racine porte les cinq champs du contrat, valeur par valeur")
        void racineConformeAuContrat() {
            EtatValideEvent charge = charge(
                    service.construire(enTete(TOTAL_ATTENDU), etat(deuxJourneesQuatreLignes())));

            assertThat(charge.idProcessus()).isEqualTo(ID_PROCESSUS);
            assertThat(charge.versionCharge())
                    .as("la charge part en version 2 : forme a bornes, sans fenetre de cohabitation")
                    .isEqualTo(2);
            assertThat(charge.periode().dateDebut()).isEqualTo(DEBUT);
            assertThat(charge.periode().dateFin()).isEqualTo(FIN);
            assertThat(charge.periode().libelle())
                    .as("le libelle se lit sur un releve de compte : c'est ce qui a emporte la forme")
                    .isEqualTo("DU 01/07/2026 AU 31/07/2026");
            assertThat(charge.compteCharge()).isEqualTo(COMPTE_CHARGE);
            assertThat(charge.codeUnite()).isEqualTo(UNITE);
            assertThat(charge.typeProcessus()).isEqualTo("NORMAL");
            assertThat(charge.montantTotal()).isEqualTo(TOTAL_ATTENDU);
        }

        @Test
        @DisplayName("chaque ligne porte les sept champs du contrat, sans date de journee")
        void lignesConformesAuContrat() {
            EtatValideEvent charge = charge(
                    service.construire(enTete(TOTAL_ATTENDU), etat(deuxJourneesQuatreLignes())));

            assertThat(charge.lignes()).hasSize(4);

            LigneEtat premiere = charge.lignes().get(0);
            assertThat(premiere.nom()).isEqualTo("Mbarga");
            assertThat(premiere.prenom()).isEqualTo("Jean");
            assertThat(premiere.numCompteCourant()).isEqualTo("00002000123456");
            assertThat(premiere.codeAgence()).isEqualTo("00002");
            assertThat(premiere.nature()).isEqualTo("RATION");
            assertThat(premiere.session()).isEqualTo("JOUR");
            assertThat(premiere.montant()).isEqualTo(2500);

            // Le contrat section 7.1 ne prevoit aucune date de journee, et CT-21 ne la
            // demande pas : la mettre inventerait un contrat que le receveur ne lit pas.
            assertThat(LigneEtat.class.getRecordComponents())
                    .extracting(java.lang.reflect.RecordComponent::getName)
                    .containsExactly("nom", "prenom", "numCompteCourant", "codeAgence", "nature",
                            "session", "montant");
        }

        @Test
        @DisplayName("les lignes de toutes les journees sont mises a plat, dans l'ordre")
        void lignesMisesAPlat() {
            EtatValideEvent charge = charge(
                    service.construire(enTete(TOTAL_ATTENDU), etat(deuxJourneesQuatreLignes())));

            assertThat(charge.lignes()).extracting(LigneEtat::montant)
                    .containsExactly(2500, 1500, 2500, 1000);
        }

        @Test
        @DisplayName("une journee ouverte sans ligne n'apporte rien a la charge")
        void journeeVideIgnoree() {
            List<EtatConsolide.Journee> journees = new ArrayList<>(deuxJourneesQuatreLignes());
            journees.add(journee(LocalDate.of(ANNEE, MOIS, 5), List.of()));

            EtatValideEvent charge = charge(
                    service.construire(enTete(TOTAL_ATTENDU), etat(journees)));

            assertThat(charge.lignes()).hasSize(4);
            assertThat(charge.montantTotal()).isEqualTo(TOTAL_ATTENDU);
        }
    }

    // --- 2. Les deux codes -------------------------------------------------------

    @Nested
    @DisplayName("Code unite et code agence : deux niveaux, jamais interchanges")
    class DeuxCodes {

        @Test
        @DisplayName("le code unite est a la racine, le code agence sur chaque ligne")
        void chaqueCodeASaPlace() {
            EtatValideEvent charge = charge(
                    service.construire(enTete(TOTAL_ATTENDU), etat(deuxJourneesQuatreLignes())));

            // Ligne de DEBIT : l'unite qui supporte la charge.
            assertThat(charge.codeUnite()).isEqualTo(UNITE);

            // Ligne de CREDIT : l'agence de domiciliation du compte du beneficiaire.
            assertThat(charge.lignes()).extracting(LigneEtat::codeAgence)
                    .containsExactly("00002", "00003", "00002", "00003");
        }

        @Test
        @DisplayName("un code agence different du code unite n'est jamais aligne dessus")
        void codeAgenceNonEcraseParCodeUnite() {
            EtatConsolide.Beneficiaire domicilieAilleurs =
                    beneficiaire("Ateba", "Rose", "00047000111222", "00047");

            EtatConsolide consolide = etat(List.of(journee(LocalDate.of(ANNEE, MOIS, 9),
                    List.of(ligne(domicilieAilleurs, "RATION", "JOUR", 3000)))));

            EtatValideEvent charge = charge(service.construire(enTete(3000), consolide));

            assertThat(charge.codeUnite()).isEqualTo("00002");
            assertThat(charge.lignes().get(0).codeAgence())
                    .withFailMessage("Le code agence du beneficiaire a ete confondu avec le code "
                            + "unite du processus : le credit partirait vers la mauvaise agence.")
                    .isEqualTo("00047");
        }

        /**
         * <b>Aucune charge ne part sans compte de charge.</b>
         *
         * <p>C'est la garantie centrale de la Maille 2, et elle se refuse plutot qu'elle
         * ne se replie. Publier un message de paiement avec un compte de charge absent
         * ou devine, c'est <b>imputer de l'argent sur le mauvais compte</b> — sans
         * qu'aucune erreur ne le signale, ni ici ni en comptabilite.
         *
         * <p>Meme doctrine que le seuil d'aiguillage au Sprint 4.3, et elle s'impose plus
         * fort encore : un seuil manquant bloque une validation, un compte manquant
         * deplace de l'argent.
         */
        @Test
        @DisplayName("aucune charge ne part sans compte de charge")
        void refusSansCompteDeCharge() {
            EnTeteProcessus sansCompte = new EnTeteProcessus(ID_PROCESSUS, "CLOTURE", UNITE,
                    DEBUT, FIN, "   ", "NORMAL", TOTAL_ATTENDU, false);

            assertThat(codes(service.construire(sansCompte, etat(deuxJourneesQuatreLignes()))))
                    .as("code propre : un compte absent est un geste d'administration, "
                            + "pas un dossier mal forme")
                    .contains(CodeAnomalieEnum.COMPTE_CHARGE_ABSENT);
        }

        /**
         * Une periode dont les bornes sont a l'envers n'a aucun sens : la comptabilite
         * ne saurait pas sur quelle periode imputer. Remplace le controle « mois hors de
         * 1 a 12 » de la version 1, qui n'a plus d'objet.
         */
        @Test
        @DisplayName("bornes de periode a l'envers")
        void refusPeriodeALEnvers() {
            EnTeteProcessus alEnvers = new EnTeteProcessus(ID_PROCESSUS, "CLOTURE", UNITE,
                    FIN, DEBUT, COMPTE_CHARGE, "NORMAL", TOTAL_ATTENDU, false);

            assertThat(codes(service.construire(alEnvers, etat(deuxJourneesQuatreLignes()))))
                    .contains(CodeAnomalieEnum.PERIODE_INVALIDE);
        }

        @DisplayName("aucune charge ne part sans code unite")
        void refusSansCodeUnite() {
            EnTeteProcessus sansUnite = new EnTeteProcessus(ID_PROCESSUS, "CLOTURE", "   ", DEBUT, FIN, COMPTE_CHARGE,
                    "NORMAL", TOTAL_ATTENDU, false);

            ResultatConstruction resultat =
                    service.construire(sansUnite, etat(deuxJourneesQuatreLignes()));

            assertThat(codes(resultat)).contains(CodeAnomalieEnum.CODE_UNITE_ABSENT);
        }

        @Test
        @DisplayName("aucune ligne ne part sans code agence")
        void refusSansCodeAgence() {
            EtatConsolide.Beneficiaire sansAgence =
                    beneficiaire("Mbarga", "Jean", "00002000123456", null);

            EtatConsolide consolide = etat(List.of(journee(LocalDate.of(ANNEE, MOIS, 3),
                    List.of(ligne(sansAgence, "RATION", "JOUR", 2500)))));

            ResultatConstruction resultat = service.construire(enTete(2500), consolide);

            assertThat(codes(resultat)).contains(CodeAnomalieEnum.LIGNE_SANS_CODE_AGENCE);
        }
    }

    // --- 3. Coherence du montant total -------------------------------------------

    @Nested
    @DisplayName("Le montant total vaut la somme des lignes")
    class CoherenceDuTotal {

        @Test
        @DisplayName("plusieurs journees et plusieurs beneficiaires : le total coincide")
        void totalEgalALaSommeSurJeuEtendu() {
            EtatValideEvent charge = charge(
                    service.construire(enTete(TOTAL_ATTENDU), etat(deuxJourneesQuatreLignes())));

            long somme = charge.lignes().stream().mapToLong(LigneEtat::montant).sum();

            assertThat(charge.montantTotal()).isEqualTo(somme).isEqualTo(TOTAL_ATTENDU);
        }

        @Test
        @DisplayName("un franc d'ecart suffit a refuser la publication")
        void unFrancDEcartRefuse() {
            ResultatConstruction resultat = service.construire(
                    enTete(TOTAL_ATTENDU + 1), etat(deuxJourneesQuatreLignes()));

            assertThat(codes(resultat)).containsExactly(CodeAnomalieEnum.TOTAL_INCOHERENT);
        }

        @Test
        @DisplayName("le message d'ecart nomme les trois temoins du montant")
        void messageNommeLesTroisTemoins() {
            ResultatConstruction resultat = service.construire(
                    enTete(9000), etat(deuxJourneesQuatreLignes()));

            String message = ((ChargeRefusee) resultat).anomalies().get(0).message();

            assertThat(message).contains("9000").contains("7500");
        }

        @Test
        @DisplayName("un total conforme mais des lignes tronquees est refuse")
        void totalConformeMaisDetailTronqueRefuse() {
            // Le processus porte 7500, le service Saisie n'a rendu que deux lignes sur
            // quatre. La somme des lignes vaut 4000 : sans ce controle, deux beneficiaires
            // seraient impayes et rien ne le signalerait.
            List<EtatConsolide.Journee> tronque =
                    List.of(deuxJourneesQuatreLignes().get(0));

            ResultatConstruction resultat = service.construire(enTete(TOTAL_ATTENDU),
                    etat(tronque));

            assertThat(codes(resultat)).contains(CodeAnomalieEnum.TOTAL_INCOHERENT);
        }

        @Test
        @DisplayName("aucun total n'est jamais ajuste sur un autre")
        void aucunAjustementSilencieux() {
            ResultatConstruction resultat = service.construire(
                    enTete(TOTAL_ATTENDU + 500), etat(deuxJourneesQuatreLignes()));

            // Rien n'est publie, et surtout aucune charge n'est rendue avec un total
            // recalcule : l'ecart doit se voir, pas se resorber.
            assertThat(resultat).isInstanceOf(ChargeRefusee.class);
        }

        @Test
        @DisplayName("un grand nombre de lignes s'additionne sans debordement")
        void sommeEntiereSansDebordement() {
            // 400 lignes a 100 000 FCFA : 40 000 000 FCFA. La somme se fait en long ;
            // un total en int deborderait bien plus haut, mais la discipline se verifie ici.
            List<EtatConsolide.Ligne> lignes = IntStream.range(0, 400)
                    .mapToObj(i -> ligne(beneficiaire("Nom" + i, "Prenom" + i,
                            String.format("%014d", i), "00002"), "RATION", "JOUR", 100_000))
                    .toList();

            EtatConsolide consolide = etat(List.of(journee(LocalDate.of(ANNEE, MOIS, 3), lignes)));

            EtatValideEvent charge = charge(service.construire(enTete(40_000_000), consolide));

            assertThat(charge.montantTotal()).isEqualTo(40_000_000L);
            assertThat(charge.lignes()).hasSize(400);
        }
    }

    // --- 4. Refus des charges incompletes ----------------------------------------

    @Nested
    @DisplayName("Charge incomplete : publication empechee, anomalie nommee")
    class ChargeIncomplete {

        @Test
        @DisplayName("etat sans aucune ligne")
        void etatVide() {
            EtatConsolide vide = new EtatConsolide(ID_PROCESSUS, UNITE, DEBUT, FIN, 0, 0, 0, 0L,
                    List.of());

            ResultatConstruction resultat = service.construire(enTete(0), vide);

            assertThat(codes(resultat)).contains(CodeAnomalieEnum.CHARGE_SANS_LIGNE,
                    CodeAnomalieEnum.MONTANT_TOTAL_INVALIDE);
        }

        @Test
        @DisplayName("ligne sans numero de compte courant")
        void ligneSansCompte() {
            EtatConsolide.Beneficiaire sansCompte = beneficiaire("Mbarga", "Jean", "  ", "00002");
            EtatConsolide consolide = etat(List.of(journee(LocalDate.of(ANNEE, MOIS, 3),
                    List.of(ligne(sansCompte, "RATION", "JOUR", 2500)))));

            assertThat(codes(service.construire(enTete(2500), consolide)))
                    .contains(CodeAnomalieEnum.LIGNE_SANS_COMPTE);
        }

        @Test
        @DisplayName("ligne sans montant : absent ou nul, meme refus")
        void ligneSansMontant() {
            EtatConsolide.Beneficiaire beneficiaire =
                    beneficiaire("Mbarga", "Jean", "00002000123456", "00002");

            EtatConsolide montantAbsent = etat(List.of(journee(LocalDate.of(ANNEE, MOIS, 3),
                    List.of(ligne(beneficiaire, "RATION", "JOUR", null)))));
            EtatConsolide montantNul = etat(List.of(journee(LocalDate.of(ANNEE, MOIS, 3),
                    List.of(ligne(beneficiaire, "RATION", "JOUR", 0)))));

            assertThat(codes(service.construire(enTete(0), montantAbsent)))
                    .contains(CodeAnomalieEnum.LIGNE_SANS_MONTANT);
            assertThat(codes(service.construire(enTete(0), montantNul)))
                    .contains(CodeAnomalieEnum.LIGNE_SANS_MONTANT);
        }

        @Test
        @DisplayName("nature ou session hors du domaine (RG-01, RG-02)")
        void natureOuSessionInconnue() {
            EtatConsolide.Beneficiaire beneficiaire =
                    beneficiaire("Mbarga", "Jean", "00002000123456", "00002");

            EtatConsolide consolide = etat(List.of(journee(LocalDate.of(ANNEE, MOIS, 3),
                    List.of(ligne(beneficiaire, "CARBURANT", "NUIT", 2500)))));

            assertThat(codes(service.construire(enTete(2500), consolide)))
                    .contains(CodeAnomalieEnum.LIGNE_NATURE_INCONNUE,
                            CodeAnomalieEnum.LIGNE_SESSION_INCONNUE);
        }

        /**
         * <b>Remplace le test « periode hors de 1 a 12 » de la version 1 de la charge.</b>
         * Le controle portait sur un numero de mois ; il porte desormais sur les bornes,
         * et « hors de 1 a 12 » n'a plus d'objet. Le cas equivalent est une borne absente.
         */
        @Test
        @DisplayName("borne de periode absente")
        void periodeInvalide() {
            EnTeteProcessus sansBorneDeFin = new EnTeteProcessus(ID_PROCESSUS, "CLOTURE", UNITE,
                    DEBUT, null, COMPTE_CHARGE, "NORMAL", TOTAL_ATTENDU, false);

            assertThat(codes(service.construire(sansBorneDeFin, etat(deuxJourneesQuatreLignes()))))
                    .contains(CodeAnomalieEnum.PERIODE_INVALIDE);
        }

        @Test
        @DisplayName("en-tete et detail portant sur des dossiers differents")
        void sourcesDiscordantes() {
            EnTeteProcessus autreUnite = new EnTeteProcessus(ID_PROCESSUS, "CLOTURE", "00050",
                    DEBUT, FIN, COMPTE_CHARGE, "NORMAL", TOTAL_ATTENDU, false);

            assertThat(codes(service.construire(autreUnite, etat(deuxJourneesQuatreLignes()))))
                    .contains(CodeAnomalieEnum.SOURCES_DISCORDANTES);
        }

        @Test
        @DisplayName("une anomalie par controle, jamais une par ligne fautive")
        void uneAnomalieParControle() {
            List<EtatConsolide.Ligne> centLignesSansAgence = IntStream.range(0, 100)
                    .mapToObj(i -> ligne(beneficiaire("Nom" + i, "Prenom" + i,
                            String.format("%014d", i), null), "RATION", "JOUR", 1000))
                    .toList();

            EtatConsolide consolide =
                    etat(List.of(journee(LocalDate.of(ANNEE, MOIS, 3), centLignesSansAgence)));

            ChargeRefusee refus =
                    (ChargeRefusee) service.construire(enTete(100_000), consolide);

            List<AnomalieCharge> sansAgence = refus.anomalies().stream()
                    .filter(a -> a.code() == CodeAnomalieEnum.LIGNE_SANS_CODE_AGENCE)
                    .toList();

            assertThat(sansAgence).hasSize(1);
            assertThat(sansAgence.get(0).message())
                    .startsWith("100 ")
                    .contains("et 95 autre(s)");
        }

        @Test
        @DisplayName("toutes les raisons sont rendues, pas seulement la premiere")
        void toutesLesRaisonsRendues() {
            EtatConsolide.Beneficiaire incomplet = beneficiaire(null, null, null, null);
            EtatConsolide consolide = etat(List.of(journee(LocalDate.of(ANNEE, MOIS, 3),
                    List.of(ligne(incomplet, null, null, null)))));

            EnTeteProcessus enTeteCasse =
                    new EnTeteProcessus(null, "CLOTURE", null, null, null, null, null, null, false);

            assertThat(codes(service.construire(enTeteCasse, consolide)))
                    .contains(CodeAnomalieEnum.IDENTIFIANT_ABSENT,
                            CodeAnomalieEnum.PERIODE_INVALIDE,
                            CodeAnomalieEnum.CODE_UNITE_ABSENT,
                            CodeAnomalieEnum.TYPE_PROCESSUS_ABSENT,
                            CodeAnomalieEnum.MONTANT_TOTAL_INVALIDE,
                            CodeAnomalieEnum.LIGNE_SANS_BENEFICIAIRE,
                            CodeAnomalieEnum.LIGNE_SANS_COMPTE,
                            CodeAnomalieEnum.LIGNE_SANS_CODE_AGENCE,
                            CodeAnomalieEnum.LIGNE_SANS_MONTANT,
                            CodeAnomalieEnum.LIGNE_NATURE_INCONNUE,
                            CodeAnomalieEnum.LIGNE_SESSION_INCONNUE);
        }

        @Test
        @DisplayName("un refus porte toujours au moins une anomalie motivee")
        void refusToujoursMotive() {
            ResultatConstruction resultat = service.construire(
                    enTete(TOTAL_ATTENDU + 1), etat(deuxJourneesQuatreLignes()));

            assertThat(((ChargeRefusee) resultat).anomalies()).isNotEmpty()
                    .allSatisfy(anomalie -> {
                        assertThat(anomalie.code()).isNotNull();
                        assertThat(anomalie.message()).isNotBlank();
                    });
        }
    }

}
