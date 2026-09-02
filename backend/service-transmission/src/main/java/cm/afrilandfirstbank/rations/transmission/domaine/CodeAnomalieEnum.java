package cm.afrilandfirstbank.rations.transmission.domaine;

/**
 * Les anomalies qui empechent une charge de partir vers la comptabilite.
 *
 * <p>Une constante par <b>controle</b>, jamais une par ligne fautive : une charge de
 * trois cents lignes toutes sans code agence produit <i>une</i> anomalie qui en nomme
 * quelques-unes et compte le reste. Meme discipline que le champ {@code manques} du
 * Sprint 4.2 — une liste se lit, une avalanche ne se lit pas.
 *
 * <p>Elles se rangent en trois familles : ce qui manque a la racine, ce qui manque sur
 * les lignes, et ce qui ne concorde pas entre les deux sources.
 */
public enum CodeAnomalieEnum {

    // --- Racine ------------------------------------------------------------------

    /** Aucun identifiant de processus : la comptabilite ne pourrait pas accuser reception. */
    IDENTIFIANT_ABSENT,

    /** Mois hors de 1 a 12, ou annee absente : la periode d'imputation serait indeterminee. */
    PERIODE_INVALIDE,

    /** Pas de code unite a la racine : la ligne de <b>debit</b> serait impossible a produire. */
    CODE_UNITE_ABSENT,

    /** Type de processus absent : normal ou complementaire change le traitement en aval. */
    TYPE_PROCESSUS_ABSENT,

    /**
     * Aucune ligne de prestation. Un etat vide n'engage rien et produirait un ordre de
     * paiement sans beneficiaire ; le controle de completude de la soumission
     * (Sprint 4.2) l'interdit deja, celui-ci est le dernier filet.
     */
    CHARGE_SANS_LIGNE,

    /** Montant total absent, nul ou negatif : rien de payable ne se presente ainsi. */
    MONTANT_TOTAL_INVALIDE,

    // --- Lignes ------------------------------------------------------------------

    /** Beneficiaire sans nom ni prenom exploitable : l'ordre de paiement serait anonyme. */
    LIGNE_SANS_BENEFICIAIRE,

    /** Numero de compte courant absent : la ligne de <b>credit</b> serait impossible a produire. */
    LIGNE_SANS_COMPTE,

    /**
     * Code agence absent. C'est l'agence de domiciliation du compte credite : sans
     * elle, la comptabilite ne sait pas ou porter le credit.
     */
    LIGNE_SANS_CODE_AGENCE,

    /** Montant de ligne absent, nul ou negatif — contredit RG-03 : le montant vient de la grille active. */
    LIGNE_SANS_MONTANT,

    /** Nature hors de RATION / TRANSPORT (RG-01). */
    LIGNE_NATURE_INCONNUE,

    /** Session hors de JOUR / SOIR (RG-02). */
    LIGNE_SESSION_INCONNUE,

    // --- Coherence ---------------------------------------------------------------

    /**
     * Le montant total ne vaut pas la somme des lignes. <b>Le controle central de ce
     * sous-sprint</b> : une fois l'evenement publie, le module n'a aucun moyen de le
     * rattraper, et un total faux est un paiement faux.
     */
    TOTAL_INCOHERENT,

    /**
     * L'en-tete lu au service Workflow et le detail lu au service Saisie ne parlent pas
     * du meme dossier : periode ou unite discordantes. Signe d'un identifiant croise ou
     * d'une reponse mal rattachee — jamais un cas metier.
     */
    SOURCES_DISCORDANTES

}
