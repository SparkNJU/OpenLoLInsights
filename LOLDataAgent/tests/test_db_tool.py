import os
import sqlite3
import json
import pytest

# prepare a temporary sqlite file
DB_PATH = 'tests_test_db.sqlite'
DB_URL = f'sqlite:///{DB_PATH}'

# create DB and tables
def setup_module(module):
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
    conn = sqlite3.connect(DB_PATH)
    cur = conn.cursor()
    cur.execute('CREATE TABLE Teams (id INTEGER PRIMARY KEY, name TEXT, code TEXT, region TEXT)')
    cur.execute("INSERT INTO Teams (id,name,code,region) VALUES (1,'EDG','EDG','CN')")
    cur.execute("INSERT INTO Teams (id,name,code,region) VALUES (2,'T1','T1','KR')")
    cur.execute('CREATE TABLE Players (id INTEGER PRIMARY KEY, name TEXT)')
    cur.execute("INSERT INTO Players (id,name) VALUES (1,'Faker')")
    cur.execute("INSERT INTO Players (id,name) VALUES (2,'Uzi')")
    conn.commit()
    conn.close()

    # set env to use this DB
    os.environ['DATABASE_URL'] = DB_URL


def teardown_module(module):
    if os.path.exists(DB_PATH):
        os.remove(DB_PATH)
    os.environ.pop('DATABASE_URL', None)


def test_count_teams_import_and_query():
    from src.tools.custom_tools.db_tool import db_query
    r = db_query('count teams')
    j = json.loads(r)
    assert 'rows' in j and len(j['rows']) == 1
    assert j['rows'][0][0] == 2


def test_list_teams():
    from src.tools.custom_tools.db_tool import db_query
    r = db_query('list teams limit 10')
    j = json.loads(r)
    assert 'rows' in j
    assert any('EDG' in row for row in j['rows'] or [])


def test_find_player():
    from src.tools.custom_tools.db_tool import db_query
    r = db_query('find player Faker')
    j = json.loads(r)
    assert 'rows' in j
    assert any('Faker' in row for row in j['rows'])


def test_select_fallback():
    from src.tools.custom_tools.db_tool import db_query
    r = db_query('SELECT name FROM Teams WHERE id=1')
    j = json.loads(r)
    assert j['rows'][0][0] == 'EDG'
