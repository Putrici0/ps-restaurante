package repository.interfaces;

import model.Rol;
import model.Usuario;

import java.util.List;

public interface UsuarioRepository extends Repository<Usuario, String> {
    List<Usuario> findByNombre(String nombre);
    List<Usuario> findByRol(Rol rol);
}
