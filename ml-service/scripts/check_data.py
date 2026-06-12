"""Check data quality in validation_logs"""
import pymysql

conn = pymysql.connect(
    host='mysql', port=3306, user='root', password='wifak2024',
    database='wifak_validation', charset='utf8mb4'
)
c = conn.cursor()

# Total counts
c.execute("SELECT action, COUNT(*) FROM validation_logs GROUP BY action")
print("=== Action counts ===")
for row in c.fetchall():
    print(f"  {row[0]}: {row[1]}")

# Declarations with REJECT+SUBMIT pairs where SUBMIT comment is short or looks wrong
c.execute("""
    SELECT r.declaration_id, 
           SUBSTRING(r.commentaire, 1, 80) as rej,
           SUBSTRING(s.commentaire, 1, 80) as sub
    FROM validation_logs r
    JOIN validation_logs s ON r.declaration_id = s.declaration_id AND s.id > r.id
    WHERE r.action = 'REJECT' AND s.action = 'SUBMIT'
    AND r.declaration_id < 30000
    AND (s.commentaire IS NULL OR LENGTH(s.commentaire) < 10)
    LIMIT 5
""")
rows = c.fetchall()
print(f"\n=== SUBMIT with empty/short correction (< 10000 decl_ids): {len(rows)} ===")
for row in rows[:3]:
    print(f"  decl={row[0]}, REJECT={row[1]}, SUBMIT={row[2]}")

# Check alignment: provision reject -> provision correction
c.execute("""
    SELECT r.declaration_id, 
           SUBSTRING(r.commentaire, 1, 80) as rej,
           SUBSTRING(s.commentaire, 1, 80) as sub
    FROM validation_logs r
    JOIN validation_logs s ON r.declaration_id = s.declaration_id AND s.id > r.id
    WHERE r.action = 'REJECT' AND s.action = 'SUBMIT'
    AND r.commentaire LIKE '%provision%'
    AND s.commentaire NOT LIKE '%provision%'
    AND r.declaration_id < 30000
    LIMIT 5
""")
rows = c.fetchall()
print(f"\n=== Provision REJECT but non-provision SUBMIT: {len(rows)} ===")
for row in rows[:5]:
    print(f"  decl={row[0]}")
    print(f"    REJECT: {row[1]}")
    print(f"    SUBMIT: {row[2]}")

# Show distribution of declaration_id ranges
c.execute("""
    SELECT 
        CASE 
            WHEN declaration_id < 1000 THEN '0-999 (manual)'
            WHEN declaration_id < 10000 THEN '1000-9999 (seed_varied)'
            WHEN declaration_id < 20000 THEN '10000-19999 (seed_massive - deleted?)'
            WHEN declaration_id < 30000 THEN '20000-29999 (other)'
            ELSE '30000+ (seed_1500_real)'
        END as range_label,
        COUNT(*) as cnt,
        action
    FROM validation_logs
    GROUP BY range_label, action
    ORDER BY range_label, action
""")
print("\n=== Distribution by ID range and action ===")
for row in c.fetchall():
    print(f"  {row[0]} | {row[2]}: {row[1]}")

conn.close()
print("\nDone.")
