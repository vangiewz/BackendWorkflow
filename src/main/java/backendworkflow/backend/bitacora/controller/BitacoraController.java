package backendworkflow.backend.bitacora.controller;


import backendworkflow.backend.bitacora.model.Bitacora;
import backendworkflow.backend.bitacora.service.BitacoraService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bitacora")
public class BitacoraController {

    @Autowired
    private BitacoraService bitacoraService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Bitacora>> obtenerTodasLasBitacoras() {
        return ResponseEntity.ok(bitacoraService.obtenerTodas());
    }
}
