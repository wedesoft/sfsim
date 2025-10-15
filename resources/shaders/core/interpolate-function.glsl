#version 410 core


const vec2 vector01 = vec2(0.0, 1.0);

float <%= base-function %>(vec3 point);

float <%= method-name %>(vec3 point)
{
  vec3 fractional = fract(point);
  vec3 integral = floor(point);
  float value000 = <%= base-function %>(integral + vector01.xxx);
  float value100 = <%= base-function %>(integral + vector01.yxx);
  float value010 = <%= base-function %>(integral + vector01.xyx);
  float value110 = <%= base-function %>(integral + vector01.yyx);
  float value001 = <%= base-function %>(integral + vector01.xxy);
  float value101 = <%= base-function %>(integral + vector01.yxy);
  float value011 = <%= base-function %>(integral + vector01.xyy);
  float value111 = <%= base-function %>(integral + vector01.yyy);
  return
    <%= interpolation %>(
        <%= interpolation %>(
          <%= interpolation %>(value000, value100, fractional.x),
          <%= interpolation %>(value010, value110, fractional.x),
          fractional.y),
        <%= interpolation %>(
          <%= interpolation %>(value001, value101, fractional.x),
          <%= interpolation %>(value011, value111, fractional.x),
          fractional.y),
        fractional.z);
}
