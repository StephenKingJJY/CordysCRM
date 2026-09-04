-- 收货人联系方式支持虚拟号、国际区号和分隔符，不再限制为 11 位手机号
UPDATE sys_module_field_blob field_blob
    JOIN sys_module_field field ON field.id = field_blob.id
SET field_blob.prop = JSON_SET(field_blob.prop, '$.format', '255')
WHERE field.internal_key = 'orderPhone'
  AND JSON_UNQUOTE(JSON_EXTRACT(field_blob.prop, '$.format')) = '11';
