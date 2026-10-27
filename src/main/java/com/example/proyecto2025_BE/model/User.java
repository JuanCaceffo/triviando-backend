package com.example.proyecto2025_BE.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.example.proyecto2025_BE.constants.DatePattern;
import com.example.proyecto2025_BE.views.Views;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "users")
public class User {
	
	@Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
	@JsonView({Views.Score.class,Views.Ranking.class, Views.GetUser.class})
	private Long id;

	@JsonView({Views.RegisterRequest.class,Views.GetUser.class,Views.UpdateUser.class})
	private String name;
	
	@JsonView({Views.RegisterRequest.class,Views.GetUser.class,Views.UpdateUser.class})
	private String lastName;

	@JsonView({Views.RegisterRequest.class,Views.GetUser.class,Views.UpdateUser.class})
	@NotBlank(groups = Views.RegisterRequest.class, message = "El email no puede estar vacío")
	@Email(groups = Views.RegisterRequest.class, message = "El email no tiene un formato válido")
	private String email;

	@JsonView({Views.RegisterRequest.class, Views.LoginRequest.class})
	@NotBlank(groups = {Views.RegisterRequest.class, Views.LoginRequest.class}, message = "La contraseña no puede estar vacía")
	private String password;

	@JsonView({Views.UpdateUser.class,Views.GetUser.class})
	private String phoneNumber;
	
	@JsonView({Views.UpdateUser.class,Views.GetUser.class})
	private String countryCode;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DatePattern.DATE)
	@JsonView({Views.GetUser.class, Views.RegisterRequest.class,Views.UpdateUser.class})
	private LocalDate birthDate;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DatePattern.DATE_TIME)
	@CreationTimestamp
	@JsonView(Views.GetUser.class)
	private LocalDateTime joinDate;

	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DatePattern.DATE_TIME)
	@UpdateTimestamp
	private LocalDateTime updatedAt;

	@JsonView({
		Views.Ranking.class,
		Views.RegisterRequest.class,
		Views.UpdateUser.class,
		Views.GetUser.class,
		Views.LoginRequest.class
	})
	@NotBlank(groups = {Views.RegisterRequest.class, Views.LoginRequest.class}, message = "El username no puede estar vacío")
	private String username;

	@JsonView(Views.Racha.class)
	private Integer rachaActual;

	@JsonView(Views.Racha.class)
	@JsonFormat(shape = JsonFormat.Shape.STRING, pattern = DatePattern.DATE)
	private LocalDate ultimaActividad;

	@JsonView(Views.Ranking.class)
	@Transient
	private int position;

	/**
	 * Score agregado por consulta. Antes se calculaba recorriendo todas las
	 * respuestas del usuario en memoria (relación EAGER).
	 */
	@Builder.Default
	@Transient
	@JsonView({Views.Ranking.class, Views.Score.class})
	private BigDecimal score = BigDecimal.ZERO;

	@JsonView({Views.GetUser.class})
	@Builder.Default
	private Account account = Account.FREE;
	
	//TODO: Crear entidades para el manejo de roles y con privilegios internos
	@Builder.Default
	private List<String> privileges = List.of("ROLE_USER");
	
	@Builder.Default
	private boolean googleAccount = false; 

	@JsonProperty("age")
	public Integer getAge() {
		return birthDate == null
				? null
				: Period.between(birthDate, LocalDate.now()).getYears();
	}

	@JsonView({Views.Ranking.class, Views.Score.class})
	public BigDecimal getScore() {
		return score == null ? BigDecimal.ZERO : score;
	}

	public void actualizarRacha() {
		LocalDate today = LocalDate.now();
		long dias = (ultimaActividad == null) ? -1 : ChronoUnit.DAYS.between(ultimaActividad, today);
		this.rachaActual = switch ((int) dias) {
			case 0 -> rachaActual;                                 // mismo día: no cambia
			case 1 -> (rachaActual == null) ? 1 : rachaActual + 1; // día consecutivo: incrementa
			default -> 1;                                          // primer uso o día salteado: reinicia
		};
		this.ultimaActividad = today;
	}

	public Integer getRachaActual() {
		return rachaActual == null ? 0 : rachaActual;
	}

	public LocalDate getUltimaActividad() {
		return ultimaActividad == null ? joinDate.toLocalDate() : ultimaActividad;
	}

	public String getFullName() { return name + " " + lastName; }

	@JsonIgnore
	public Collection<? extends GrantedAuthority> getAuthorities(){
		return this.getPrivileges().stream().map(SimpleGrantedAuthority::new).toList();
	}
}