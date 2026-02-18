"""
Unit-тесты для BionicPRO ETL DAG
"""
import unittest
from unittest.mock import Mock, patch, MagicMock, mock_open
import pandas as pd
from datetime import datetime, timedelta
import sys
import os

# Добавляем путь для импорта DAG
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

# Мокаем модули перед импортом DAG
with patch('psycopg2.connect') as mock_pg_connect, \
     patch('clickhouse_driver.Client') as mock_ch_client:
    from bionicpro_etl_dag import (
        dag,
        extract_sensors_data,
        extract_crm_data,
        transform_and_merge_data,
        load_to_olap,
        default_args
    )


class TestExtractSensorsData(unittest.TestCase):
    """Тесты для функции extract_sensors_data"""
    
    @patch('bionicpro_etl_dag.pd.read_sql')
    @patch('bionicpro_etl_dag.psycopg2.connect')
    def test_extract_sensors_data_success(self, mock_connect, mock_read_sql):
        """Тест успешного извлечения данных из sensors-db"""
        # Подготовка мок-данных
        mock_df = pd.DataFrame({
            'user_id': [1, 2],
            'prosthesis_type': ['arm', 'leg'],
            'muscle_group': ['biceps', 'quadriceps'],
            'signal_frequency': [50.0, 60.0],
            'signal_duration': [100.0, 200.0],
            'signal_amplitude': [0.5, 0.8],
            'signal_time': [datetime.now(), datetime.now()]
        })
        mock_read_sql.return_value = mock_df
        
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        
        # Выполнение
        result = extract_sensors_data(ds='2024-01-01', tomorrow_ds='2024-01-02')
        
        # Проверка
        self.assertEqual(result, 2)
        mock_connect.assert_called_once()
        mock_read_sql.assert_called_once()
        mock_conn.close.assert_called_once()
    
    @patch('bionicpro_etl_dag.pd.read_sql')
    @patch('bionicpro_etl_dag.psycopg2.connect')
    def test_extract_sensors_data_empty(self, mock_connect, mock_read_sql):
        """Тест обработки пустых данных из sensors-db"""
        # Подготовка пустого DataFrame
        mock_df = pd.DataFrame(columns=[
            'user_id', 'prosthesis_type', 'muscle_group',
            'signal_frequency', 'signal_duration', 'signal_amplitude', 'signal_time'
        ])
        mock_read_sql.return_value = mock_df
        
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        
        # Выполнение
        result = extract_sensors_data(ds='2024-01-01', tomorrow_ds='2024-01-02')
        
        # Проверка
        self.assertEqual(result, 0)
    
    @patch('bionicpro_etl_dag.psycopg2.connect')
    def test_extract_sensors_data_connection_error(self, mock_connect):
        """Тест обработки ошибки подключения к sensors-db"""
        # Настройка мока для выброса исключения
        mock_connect.side_effect = Exception("Connection refused")
        
        # Выполнение и проверка
        with self.assertRaises(Exception) as context:
            extract_sensors_data(ds='2024-01-01', tomorrow_ds='2024-01-02')
        
        self.assertIn("Connection refused", str(context.exception))


class TestExtractCrmData(unittest.TestCase):
    """Тесты для функции extract_crm_data"""
    
    @patch('bionicpro_etl_dag.pd.read_sql')
    @patch('bionicpro_etl_dag.psycopg2.connect')
    def test_extract_crm_data_success(self, mock_connect, mock_read_sql):
        """Тест успешного извлечения данных из CRM"""
        # Подготовка мок-данных
        mock_df = pd.DataFrame({
            'user_id': [1, 2, 3],
            'name': ['John Doe', 'Jane Smith', 'Bob Johnson'],
            'email': ['john@example.com', 'jane@example.com', 'bob@example.com'],
            'age': [30, 25, 40],
            'gender': ['M', 'F', 'M'],
            'country': ['USA', 'UK', 'Germany']
        })
        mock_read_sql.return_value = mock_df
        
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        
        # Выполнение
        result = extract_crm_data()
        
        # Проверка
        self.assertEqual(result, 3)
        mock_connect.assert_called_once()
        mock_read_sql.assert_called_once()
        mock_conn.close.assert_called_once()
    
    @patch('bionicpro_etl_dag.pd.read_sql')
    @patch('bionicpro_etl_dag.psycopg2.connect')
    def test_extract_crm_data_empty(self, mock_connect, mock_read_sql):
        """Тест обработки пустых данных из CRM"""
        # Подготовка пустого DataFrame
        mock_df = pd.DataFrame(columns=[
            'user_id', 'name', 'email', 'age', 'gender', 'country'
        ])
        mock_read_sql.return_value = mock_df
        
        mock_conn = MagicMock()
        mock_connect.return_value = mock_conn
        
        # Выполнение
        result = extract_crm_data()
        
        # Проверка
        self.assertEqual(result, 0)
    
    @patch('bionicpro_etl_dag.psycopg2.connect')
    def test_extract_crm_data_connection_error(self, mock_connect):
        """Тест обработки ошибки подключения к CRM"""
        # Настройка мока для выброса исключения
        mock_connect.side_effect = Exception("Connection refused")
        
        # Выполнение и проверка
        with self.assertRaises(Exception) as context:
            extract_crm_data()
        
        self.assertIn("Connection refused", str(context.exception))


class TestTransformAndMergeData(unittest.TestCase):
    """Тесты для функции transform_and_merge_data"""
    
    @patch('bionicpro_etl_dag.pd.read_csv')
    def test_transform_data_with_valid_input(self, mock_read_csv):
        """Тест трансформации с валидными данными"""
        # Подготовка мок-данных для sensors
        sensors_df = pd.DataFrame({
            'user_id': [1, 1, 2],
            'prosthesis_type': ['arm', 'arm', 'leg'],
            'muscle_group': ['biceps', 'biceps', 'quadriceps'],
            'signal_frequency': [50.0, 55.0, 60.0],
            'signal_duration': [100.0, 200.0, 300.0],
            'signal_amplitude': [0.5, 0.8, 0.7]
        })
        
        # Подготовка мок-данных для CRM
        crm_df = pd.DataFrame({
            'user_id': [1, 2],
            'name': ['John Doe', 'Jane Smith'],
            'email': ['john@example.com', 'jane@example.com'],
            'age': [30, 25],
            'gender': ['M', 'F'],
            'country': ['USA', 'UK']
        })
        
        # Настройка моков для чтения файлов
        def read_csv_side_effect(filename):
            if 'sensors' in filename:
                return sensors_df
            elif 'crm' in filename:
                return crm_df
            return pd.DataFrame()
        
        mock_read_csv.side_effect = read_csv_side_effect
        
        # Выполнение
        result = transform_and_merge_data(ds='2024-01-01')
        
        # Проверка
        self.assertGreater(result, 0)
    
    @patch('bionicpro_etl_dag.pd.read_csv')
    def test_transform_data_with_empty_sensors(self, mock_read_csv):
        """Тест обработки пустых данных sensors"""
        # Подготовка пустых данных sensors
        sensors_df = pd.DataFrame(columns=[
            'user_id', 'prosthesis_type', 'muscle_group',
            'signal_frequency', 'signal_duration', 'signal_amplitude'
        ])
        
        # Подготовка CRM данных
        crm_df = pd.DataFrame({
            'user_id': [1],
            'name': ['John Doe'],
            'email': ['john@example.com'],
            'age': [30],
            'gender': ['M'],
            'country': ['USA']
        })
        
        # Настройка моков
        def read_csv_side_effect(filename):
            if 'sensors' in filename:
                return sensors_df
            elif 'crm' in filename:
                return crm_df
            return pd.DataFrame()
        
        mock_read_csv.side_effect = read_csv_side_effect
        
        # Выполнение - должна обработать пустой DataFrame
        result = transform_and_merge_data(ds='2024-01-01')
        
        # Проверка - результат может быть 0 из-за пустых данных
        self.assertEqual(result, 0)
    
    @patch('bionicpro_etl_dag.pd.read_csv')
    def test_transform_data_with_empty_crm(self, mock_read_csv):
        """Тест обработки пустых данных CRM"""
        # Подготовка данных sensors
        sensors_df = pd.DataFrame({
            'user_id': [1],
            'prosthesis_type': ['arm'],
            'muscle_group': ['biceps'],
            'signal_frequency': [50.0],
            'signal_duration': [100.0],
            'signal_amplitude': [0.5]
        })
        
        # Подготовка пустых данных CRM
        crm_df = pd.DataFrame(columns=[
            'user_id', 'name', 'email', 'age', 'gender', 'country'
        ])
        
        # Настройка моков
        def read_csv_side_effect(filename):
            if 'sensors' in filename:
                return sensors_df
            elif 'crm' in filename:
                return crm_df
            return pd.DataFrame()
        
        mock_read_csv.side_effect = read_csv_side_effect
        
        # Выполнение
        result = transform_and_merge_data(ds='2024-01-01')
        
        # Проверка
        self.assertGreaterEqual(result, 0)
    
    @patch('bionicpro_etl_dag.pd.read_csv')
    def test_transform_data_aggregation(self, mock_read_csv):
        """Тест агрегации данных"""
        # Подготовка данных с несколькими записями для агрегации
        sensors_df = pd.DataFrame({
            'user_id': [1, 1, 1, 2, 2],
            'prosthesis_type': ['arm', 'arm', 'arm', 'leg', 'leg'],
            'muscle_group': ['biceps', 'biceps', 'biceps', 'quadriceps', 'quadriceps'],
            'signal_frequency': [50.0, 55.0, 60.0, 45.0, 50.0],
            'signal_duration': [100.0, 200.0, 300.0, 150.0, 250.0],
            'signal_amplitude': [0.5, 0.8, 0.7, 0.6, 0.9]
        })
        
        crm_df = pd.DataFrame({
            'user_id': [1, 2],
            'name': ['John Doe', 'Jane Smith'],
            'email': ['john@example.com', 'jane@example.com'],
            'age': [30, 25],
            'gender': ['M', 'F'],
            'country': ['USA', 'UK']
        })
        
        def read_csv_side_effect(filename):
            if 'sensors' in filename:
                return sensors_df
            elif 'crm' in filename:
                return crm_df
            return pd.DataFrame()
        
        mock_read_csv.side_effect = read_csv_side_effect
        
        # Выполнение
        result = transform_and_merge_data(ds='2024-01-01')
        
        # Проверка - должно быть 2 агрегированные записи (по одному на user_id)
        self.assertEqual(result, 2)


class TestLoadToOlap(unittest.TestCase):
    """Тесты для функции load_to_olap"""
    
    @patch('bionicpro_etl_dag.pd.read_csv')
    @patch('bionicpro_etl_dag.Client')
    def test_load_to_olap_valid_data(self, mock_client_class, mock_read_csv):
        """Тест успешной загрузки данных в ClickHouse"""
        # Подготовка мок-данных
        mock_df = pd.DataFrame({
            'user_id': [1, 2],
            'report_date': ['2024-01-01', '2024-01-01'],
            'total_sessions': [10, 15],
            'avg_signal_amplitude': [0.6, 0.7],
            'max_signal_amplitude': [0.8, 0.9],
            'min_signal_amplitude': [0.4, 0.5],
            'avg_signal_frequency': [50.0, 55.0],
            'total_usage_hours': [5.0, 6.0],
            'prosthesis_type': ['arm', 'leg'],
            'muscle_group': ['biceps', 'quadriceps'],
            'customer_name': ['John Doe', 'Jane Smith'],
            'customer_email': ['john@example.com', 'jane@example.com'],
            'customer_age': [30, 25],
            'customer_gender': ['M', 'F'],
            'customer_country': ['USA', 'UK']
        })
        mock_read_csv.return_value = mock_df
        
        mock_client = MagicMock()
        mock_client_class.return_value = mock_client
        
        # Выполнение
        result = load_to_olap()
        
        # Проверка
        self.assertEqual(result, 2)
        mock_client.execute.assert_called()
    
    @patch('bionicpro_etl_dag.pd.read_csv')
    @patch('bionicpro_etl_dag.Client')
    def test_load_to_olap_empty_data(self, mock_client_class, mock_read_csv):
        """Тест обработки пустых данных при загрузке"""
        # Подготовка пустого DataFrame
        mock_df = pd.DataFrame(columns=[
            'user_id', 'report_date', 'total_sessions',
            'avg_signal_amplitude', 'max_signal_amplitude', 'min_signal_amplitude',
            'avg_signal_frequency', 'total_usage_hours', 'prosthesis_type',
            'muscle_group', 'customer_name', 'customer_email',
            'customer_age', 'customer_gender', 'customer_country'
        ])
        mock_read_csv.return_value = mock_df
        
        mock_client = MagicMock()
        mock_client_class.return_value = mock_client
        
        # Выполнение
        result = load_to_olap()
        
        # Проверка
        self.assertEqual(result, 0)
    
    @patch('bionicpro_etl_dag.pd.read_csv')
    @patch('bionicpro_etl_dag.Client')
    def test_load_to_olap_connection_error(self, mock_client_class, mock_read_csv):
        """Тест обработки ошибки подключения к ClickHouse"""
        # Подготовка данных
        mock_df = pd.DataFrame({
            'user_id': [1],
            'report_date': ['2024-01-01'],
            'total_sessions': [10],
        })
        mock_read_csv.return_value = mock_df
        
        # Настройка мока для выброса исключения
        mock_client_class.side_effect = Exception("ClickHouse connection error")
        
        # Выполнение и проверка
        with self.assertRaises(Exception) as context:
            load_to_olap()
        
        self.assertIn("ClickHouse connection error", str(context.exception))


class TestDagStructure(unittest.TestCase):
    """Тесты для структуры DAG"""
    
    def test_dag_task_order(self):
        """Тест проверки порядка тасков в DAG"""
        # Получаем все таски из DAG
        tasks = dag.task_dict
        
        # Проверяем наличие необходимых тасков
        self.assertIn('extract_sensors_data', tasks)
        self.assertIn('extract_crm_data', tasks)
        self.assertIn('transform_and_merge_data', tasks)
        self.assertIn('load_to_olap', tasks)
        
        # Проверяем порядок выполнения (dependencies)
        transform_task = tasks['transform_and_merge_data']
        load_task = tasks['load_to_olap']
        
        # transform_data должен зависеть от extract тасков
        upstream_tasks = transform_task.upstream_task_ids
        self.assertIn('extract_sensors_data', upstream_tasks)
        self.assertIn('extract_crm_data', upstream_tasks)
        
        # load_to_olap должен зависеть от transform_data
        load_upstream = load_task.upstream_task_ids
        self.assertIn('transform_and_merge_data', load_upstream)
    
    def test_dag_schedule(self):
        """Тест проверки расписания DAG"""
        # Проверяем schedule_interval
        self.assertEqual(dag.schedule_interval, '0 2 * * *')
    
    def test_dag_retries(self):
        """Тест проверки retry политики"""
        # Проверяем параметры по умолчанию
        self.assertEqual(default_args['retries'], 3)
        self.assertEqual(default_args['retry_delay'], timedelta(minutes=5))
        
        # Проверяем, что все таски используют эти параметры
        for task in dag.task_dict.values():
            self.assertEqual(task.retries, 3)
            self.assertEqual(task.retry_delay, timedelta(minutes=5))


if __name__ == '__main__':
    unittest.main()
