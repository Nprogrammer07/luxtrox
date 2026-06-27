package com.luxtrox.backend.health;

/**
 * Variables de entorno totalmente AUSENTES ya hacen que la app no
 * arranque (application.yml las define como ${VAR} sin valor por
 * defecto -- Spring falla duro e inmediato en ese caso, no en
 * silencio). Lo que esto detecta es el caso que SI pasa
 * desapercibido: una variable que existe pero quedo vacia (algunas
 * plataformas de despliegue lo permiten sin que cuente como "no
 * definida"), o el caso raro de que el placeholder sin resolver se
 * haya filtrado tal cual ("${NOWPAYMENTS_API_KEY}") por algun
 * mecanismo de configuracion no estandar.
 */
final class ConfigHealthCheck {

    private ConfigHealthCheck() {
    }

    static boolean isMissing(String value) {
        return value == null || value.isBlank() || value.startsWith("${");
    }
}