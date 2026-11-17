-- V11: perfil base del radar OECE/OCDS.
-- Sin al menos un perfil activo el motor no produce coincidencias y la vista
-- /operaciones/licitaciones queda vacia aunque la sincronizacion tenga exito.
-- Las palabras se guardan en minuscula y sin tildes porque TenderMatchEngine
-- normaliza ambos lados antes de comparar.

INSERT INTO tender_match_profiles (
    public_id, name, include_keywords, exclude_keywords, buyers, regions,
    classification_codes, min_amount, max_amount, closing_from, closing_to, active)
SELECT UUID(),
       'Tecnologia y equipamiento TI',
       '["toner","cartucho de tinta","impresora","fotocopiadora","escaner","computadora","equipo de computo","laptop","monitores","servidor","switch","router","red de datos","cableado estructurado","informatica","informatico","proyector","disco duro","memoria ram","camara","videovigilancia","bodycam","suministro de impresion"]',
       '["ejecucion de obra","consultoria de obra","supervision de obra"]',
       '[]',
       '[]',
       '[]',
       NULL, NULL, NULL, NULL, TRUE
WHERE NOT EXISTS (SELECT 1 FROM tender_match_profiles);
