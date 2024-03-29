#version 410 core

uniform float radius;
uniform float max_height;
uniform vec3 light_direction;
<% (if textured %>
uniform sampler2D colors;
<% %>
uniform vec3 diffuse_color;
<% ) %>
<% (if bump %>
uniform sampler2D normals;
<% ) %>

in VS_OUT
{
  vec3 point;
<% (if bump %>
  mat3 surface;
<% %>
  vec3 normal;
<% ) %>
<% (if (or textured bump) %>
  vec2 texcoord;
<% ) %>
} fs_in;

out vec4 fragColor;

vec3 direct_light(vec3 point);
vec3 phong(vec3 ambient, vec3 light, vec3 point, vec3 normal, vec3 color, float reflectivity);
vec3 attenuation_point(vec3 point, vec3 incoming);
vec3 surface_radiance_function(vec3 point, vec3 light_direction);
vec4 cloud_point(vec3 point);

void main()
{
  vec3 light = direct_light(fs_in.point);
  vec3 ambient_light = surface_radiance_function(fs_in.point, light_direction);
<% (if bump %>
  vec3 normal = fs_in.surface * (2.0 * texture(normals, fs_in.texcoord).xyz - 1.0);
<% ) %>
<% (if textured %>
  vec3 diffuse_color = texture(colors, fs_in.texcoord).rgb;
<% ) %>
  vec3 incoming = phong(ambient_light, light, fs_in.point, <% (if (not bump) %>fs_in.<% ) %>normal, diffuse_color, 0.0);
  incoming = attenuation_point(fs_in.point, incoming);
  vec4 cloud_scatter = cloud_point(fs_in.point);
  fragColor = vec4(incoming, 1.0) * (1 - cloud_scatter.a) + cloud_scatter;
}
