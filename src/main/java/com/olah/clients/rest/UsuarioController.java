package com.olah.clients.rest;

import com.olah.clients.model.dominio.DominioPerfilUsuario;
import com.olah.clients.model.entity.Usuario;
import com.olah.clients.exception.UsuarioCadastradoException;
import com.olah.clients.model.repository.UsuarioRepository;
import com.olah.clients.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import javax.validation.Valid;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private EntityManager entity;


    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USUARIO_MASTER','USUARIO_ADMINISTRADOR')")
    public Page<Usuario> obterTodos( @RequestParam(value= "page", defaultValue = "0") Integer pagina,
                                     @RequestParam(value = "size", defaultValue = "10") Integer tamanhoPagina) {
        PageRequest pageRequest = PageRequest.of(pagina, tamanhoPagina);
        return repository.findAll(pageRequest);
    }

    @GetMapping("/perfil={perfil}")
    @PreAuthorize("hasAnyRole('USUARIO_MASTER','USUARIO_ADMINISTRADOR')")
    public Page<Usuario> obterUsuariosPorPerfil(@PathVariable Integer perfil,
                                                @RequestParam(value= "page", defaultValue = "0") Integer pagina,
                                                @RequestParam(value = "size", defaultValue = "10") Integer tamanhoPagina,
                                                @SortDefault(sort = "dataCadastro", direction = Sort.Direction.DESC) Sort sort) {
        Pageable pageable = PageRequest.of(pagina, tamanhoPagina, sort);
        return repository.findByPerfil(perfil, pageable);
    }

    @GetMapping("/likeNomeMembro={nome}")
    @PreAuthorize("hasAnyRole('USUARIO_MASTER','USUARIO_ADMINISTRADOR')")
    public List<Usuario> obterMembrosPorNomeLike(@PathVariable String nome) {
        Query query = entity.createQuery("select u from Usuario u where u.perfil =?1 and UPPER(u.nome) like UPPER(?2)", Usuario.class);
        query.setParameter(1, DominioPerfilUsuario.MEMBRO.ordinal());
        query.setParameter(2, nome+"%");
        List<Usuario> usuarios = query.getResultList();
        return usuarios;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('USUARIO_MASTER','USUARIO_ADMINISTRADOR')")
    public void salvar(@RequestBody @Valid Usuario usuario) {
        try {
            usuarioService.salvar(usuario);
        } catch (UsuarioCadastradoException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequestMapping("/autocadastro")
    public void autocadastro(@RequestBody @Valid Usuario usuario) {
        try {
            usuarioService.salvar(usuario);
        } catch (UsuarioCadastradoException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("{id}")
    public Usuario selectPorId(@PathVariable Integer id) {
        return repository.findById(id)
                .orElseThrow( () -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @GetMapping("/pesquisaCredencial={credencial}")
    public Usuario selectPorCredencial(@PathVariable String credencial) {
        Usuario usuario;
        usuario = repository.findByLogin(credencial);
        if (usuario == null) {
            return repository.findByEmail(credencial)
                    .orElseThrow( () -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        }
        return usuario;
    }

    @PutMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void atualizar(@PathVariable Integer id, @RequestBody @Valid Usuario usuarioAtualizado) {
        repository.findById(id)
                .map( usuario -> {
                    usuarioAtualizado.setId(id);
                    return repository.save(usuarioAtualizado);
                })
                .orElseThrow( () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    @RequestMapping("inativar")
    @PutMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('USUARIO_MASTER','USUARIO_ADMINISTRADOR')")
    public void inativar(@PathVariable Integer id, @RequestBody @Valid Usuario usuarioInativado) {
        repository.findById(id)
                .map( usuario -> {
                    usuarioInativado.setId(id);
                    usuarioInativado.setDataInativacao(new Date());
                    return repository.save(usuarioInativado);
                })
                .orElseThrow( () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));
    }

    @DeleteMapping("{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('USUARIO_MASTER','USUARIO_ADMINISTRADOR')")
    public void deletar( @PathVariable Integer id) {
        repository.findById(id)
                .map(usuario -> {
                    repository.delete(usuario);
                    return Void.TYPE;
                })
                .orElseThrow( () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
    }

    @PutMapping("/converter={id}")
    @PreAuthorize("hasAnyRole('USUARIO_ADMINISTRADOR')")
    @Transactional
    public Integer converterUsuarioEmMembro(@PathVariable Integer id) {
        int retorno = entity.createQuery("update Usuario set perfil=1,dataConversao=current_date where perfil != 1 and id ="+id).executeUpdate();
        return retorno;
    }

}
