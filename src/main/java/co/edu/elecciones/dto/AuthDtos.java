package co.edu.elecciones.dto; import co.edu.elecciones.domain.Role;
public class AuthDtos { public record LoginRequest(String email,String password){} public record LoginResponse(String token,String tokenType,Long userId,String name,String email,Role role){} public record MeResponse(Long userId,String name,String email,Role role){} }
