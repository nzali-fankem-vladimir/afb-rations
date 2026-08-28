package cm.afrilandfirstbank.rations.grilles.domaine;

import java.time.LocalDate;

/**
 * Resultat d'une resolution de montant (RG-03) : soit un montant applicable et
 * la grille qui l'a fourni, soit une indisponibilite explicite.
 *
 * <p><b>Pourquoi un type dedie plutot qu'un {@code Optional<GrilleTarifaire>}.</b>
 * Un {@code Optional} invite a la ligne qui ruinerait RG-03 :
 * {@code .map(GrilleTarifaire::getMontantFcfa).orElse(0)}. Elle compile, elle se
 * lit bien, et elle enregistre des prestations a montant nul qui partent en
 * comptabilite sans que personne ne s'en apercoive. Ce type ne propose aucun
 * repli : l'appelant doit lire {@link #disponible()} pour savoir s'il y a un
 * montant, et {@link #montantFcfa()} vaut {@code null} — jamais zero — quand il
 * n'y en a pas.
 *
 * <p>L'indisponibilite porte tout de meme la question posee (nature, session,
 * date), pour que l'appelant puisse formuler un message a l'agent sans avoir a
 * conserver sa propre requete.
 *
 * @param disponible vrai si une grille couvre la date demandee
 * @param nature nature demandee, toujours renseignee
 * @param session session demandee, toujours renseignee
 * @param date date de la prestation, telle que demandee
 * @param montantFcfa montant applicable, {@code null} si indisponible
 * @param idGrille identifiant de la grille retenue, {@code null} si indisponible
 * @param dateDebut premier jour de validite de la grille retenue, {@code null} si indisponible
 * @param dateFin dernier jour de validite, {@code null} si la grille est courante ou si indisponible
 */
public record ResolutionMontant(
        boolean disponible,
        NatureEnum nature,
        SessionEnum session,
        LocalDate date,
        Integer montantFcfa,
        Long idGrille,
        LocalDate dateDebut,
        LocalDate dateFin) {

    /** Une grille couvre la date : le montant vient d'elle, et elle est nommee. */
    public static ResolutionMontant trouve(NatureEnum nature, SessionEnum session, LocalDate date,
            GrilleTarifaire grille) {
        return new ResolutionMontant(true, nature, session, date,
                grille.getMontantFcfa(), grille.getId(), grille.getDateDebut(), grille.getDateFin());
    }

    /**
     * Aucune grille ne couvre la date.
     *
     * <p>Reponse legitime, pas un echec : elle recouvre aussi bien une date
     * anterieure a tout l'historique qu'une periode fermee sans remplacante. Le
     * service Grilles ne distingue pas les deux, et l'agent n'agirait pas
     * differemment : dans les deux cas, aucune prestation n'est tarifable ce
     * jour-la.
     */
    public static ResolutionMontant indisponible(NatureEnum nature, SessionEnum session, LocalDate date) {
        return new ResolutionMontant(false, nature, session, date, null, null, null, null);
    }

}
