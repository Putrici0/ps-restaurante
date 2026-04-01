package repository.interfaces;

import model.Cuenta;
import model.Mesa;

import java.util.List;
import java.util.Optional;

public interface CuentaRepository extends Repository<Cuenta, String> {
    Optional<Cuenta> findByMesa(Mesa mesa);
    List<Cuenta> findByEstaPagada(boolean estaPagada);
}
