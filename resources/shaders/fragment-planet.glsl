#version 410 core
uniform sampler2D colors;
uniform sampler2D normals;
uniform sampler2D transmittance;
uniform sampler2D surface_radiance;
uniform sampler2D water;
uniform int size;
uniform float power;
uniform float albedo;
uniform float radius;
uniform float max_height;
uniform vec3 water_color;
uniform vec3 light;
in GEO_OUT
{
  mediump vec2 colorcoord;
  mediump vec2 heightcoord;
  highp vec3 point;
} fs_in;
out lowp vec3 fragColor;
float M_PI = 3.14159265358;  // TODO: remove this
vec2 transmittance_forward(vec3 point, vec3 direction, float radius, float max_height, int size, float power);  // TODO: remove
vec4 interpolate_2d(sampler2D table, int size, vec2 idx);  // TODO: remove this
void main()
{
  vec3 normal = texture(normals, fs_in.colorcoord).xyz;
  vec3 land_color = texture(colors, fs_in.colorcoord).rgb;
  float wet = texture(water, fs_in.colorcoord).r;
  float cos_incidence = max(dot(light, normal), 0);
  vec2 idx = transmittance_forward(fs_in.point, light, radius, max_height, size, power);
  vec3 direct_light = interpolate_2d(transmittance, size, idx).rgb;
  vec3 ambient_light = interpolate_2d(surface_radiance, size, idx).rgb;
  vec3 color = land_color * (1 - wet) + water_color * wet;
  fragColor = (albedo / M_PI) * color * (cos_incidence * direct_light + ambient_light);
}
