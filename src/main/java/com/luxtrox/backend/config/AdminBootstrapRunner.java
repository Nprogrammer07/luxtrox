package com.luxtrox.backend.config;

import com.luxtrox.backend.entity.Role;
import com.luxtrox.backend.entity.User;
import com.luxtrox.backend.repository.RoleRepository;
import com.luxtrox.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resuelve el problema de "el huevo y la gallina" del primer admin: el
 * registro publico SIEMPRE asigna rol USER (correcto -- nadie deberia
 * poder auto-asignarse ADMIN por la API), pero entonces, ¿como se crea
 * el PRIMER admin en un entorno de produccion donde no hay acceso
 * directo y comodo a la base de datos?
 *
 * Flujo:
 *   1. Registrate normal por POST /auth/register (te da rol USER).
 *   2. Configura la variable de entorno BOOTSTRAP_ADMIN_EMAIL con ese
 *      mismo correo.
 *   3. Reinicia la app (o espera al siguiente deploy). Este runner
 *      corre una vez al arrancar, encuentra ese usuario, y lo promueve
 *      a ADMIN.
 *   4. IMPORTANTE: quita la variable de entorno despues de confirmar
 *      la promocion. Si la dejas puesta y mas adelante DEGRADAS a ese
 *      usuario (ej. le quitas el rol porque ya no deberia ser admin),
 *      el siguiente reinicio lo volveria a promover sin que lo
 *      pidieras -- este runner es idempotente hacia adelante (no hace
 *      nada si ya es ADMIN), pero no sabe distinguir "todavia no lo
 *      promovi" de "lo promovi y despues alguien lo degrado a
 *      proposito".
 *
 * A proposito NO es un endpoint HTTP: un endpoint de bootstrap, aunque
 * este protegido por un secreto, es superficie de ataque que queda
 * expuesta PARA SIEMPRE en la red. Esto, al no ser una ruta HTTP, solo
 * lo puede activar quien YA tiene acceso al panel de variables de
 * entorno de la plataforma de despliegue -- no hay nada nuevo que
 * proteger en la red.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final String bootstrapAdminEmail;

    public AdminBootstrapRunner(UserRepository userRepository,
                                 RoleRepository roleRepository,
                                 @Value("${app.bootstrap-admin-email:}") String bootstrapAdminEmail) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.bootstrapAdminEmail = bootstrapAdminEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapAdminEmail == null || bootstrapAdminEmail.isBlank()) {
            return; // no configurado -- el caso normal en el dia a dia, no hace nada
        }

        Optional<User> userOpt = userRepository.findByEmail(bootstrapAdminEmail);
        if (userOpt.isEmpty()) {
            log.warn("BOOTSTRAP_ADMIN_EMAIL apunta a '{}', pero ese usuario no existe todavia. " +
                    "Registrate primero con ese correo via POST /auth/register, luego reinicia la app.",
                    bootstrapAdminEmail);
            return;
        }

        User user = userOpt.get();
        if ("ADMIN".equals(user.getRole().getName())) {
            return; // ya es admin -- idempotente, no hace ruido en cada reinicio
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("El rol ADMIN no existe en la base de datos"));
        user.setRole(adminRole);
        userRepository.save(user);

        log.warn("Usuario '{}' promovido a ADMIN via BOOTSTRAP_ADMIN_EMAIL. " +
                "Quita esa variable de entorno ahora que la promocion se confirmo.", bootstrapAdminEmail);
    }
}
